[CmdletBinding()]
param(
    [ValidateRange(30, 3600)][int]$StartupTimeoutSeconds = 300,
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$services = @(
    @{ Module = 'discovery-server'; Port = 8761; App = $null },
    @{ Module = 'user-service'; Port = 8081; App = 'USER-SERVICE' },
    @{ Module = 'Contact_Service'; Port = 8082; App = 'CONTACT-SERVICE' },
    @{ Module = 'TransactionMicroservice'; Port = 8083; App = 'TRANSACTIONMICROSERVICE' },
    @{ Module = 'api-gateway'; Port = 8080; App = 'API-GATEWAY' }
)
$started = @()
$savedEnvironment = @{}

function Set-LaunchEnvironment([string]$Name, [string]$Value) {
    if (-not $savedEnvironment.ContainsKey($Name)) {
        $savedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $Port)
        return ($connection.Wait(500) -and $client.Connected)
    } catch { return $false }
    finally { $client.Dispose() }
}

function Test-ServiceReady($Service) {
    if (-not (Test-Port $Service.Port)) { return $false }
    try {
        if ($Service.Module -ne 'TransactionMicroservice') {
            $health = Invoke-RestMethod "http://127.0.0.1:$($Service.Port)/actuator/health" -TimeoutSec 3
            if ($health.status -ne 'UP') { return $false }
        }
        if ($Service.App) {
            $registration = Invoke-RestMethod "http://127.0.0.1:8761/eureka/apps/$($Service.App)" -Headers @{ Accept = 'application/json' } -TimeoutSec 3
            return (@($registration.application.instance | Where-Object {
                $_.status -eq 'UP' -and [int]$_.port.'$' -eq $Service.Port
            }).Count -gt 0)
        }
        return $true
    } catch { return $false }
}

try {
    # Optional local secrets file. Parse assignments as data, never execute them.
    $envFile = Join-Path $PSScriptRoot '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^\s*(#|$)') { continue }
            if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
                throw 'Invalid .env entry. Use one NAME=value assignment per line.'
            }
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            Set-LaunchEnvironment $name $value
        }
    }

    $required = 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_PRIVATE_KEY', 'JWT_PUBLIC_KEY', 'INTERNAL_SERVICE_TOKEN'
    $missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
    if ($missing.Count) { throw "Missing environment variables: $($missing -join ', '). Set them in your shell or backend .env (see .env.example). IntelliJ run configuration variables are not inherited." }
    if ($env:USER_SERVICE_INTERNAL_TOKEN -and $env:USER_SERVICE_INTERNAL_TOKEN -ne $env:INTERNAL_SERVICE_TOKEN) {
        throw 'USER_SERVICE_INTERNAL_TOKEN and INTERNAL_SERVICE_TOKEN must match.'
    }
    Set-LaunchEnvironment 'USER_SERVICE_INTERNAL_TOKEN' $env:INTERNAL_SERVICE_TOKEN
    Set-LaunchEnvironment 'EUREKA_URL' 'http://localhost:8761/eureka/'

    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
    if (-not (Test-Path -LiteralPath $java)) { throw 'Java was not found. Set JAVA_HOME to your JDK 24 installation.' }
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'mvnw.cmd'))) { throw 'mvnw.cmd is missing.' }
    foreach ($service in $services) {
        if (Test-Port $service.Port) { throw "Port $($service.Port) is already in use. Stop the existing service before launching all services." }
    }
    if ($Check) {
        Write-Host 'Preflight passed: environment, Java location, Maven wrapper, and ports checked. Database and Java version are not checked.'
        return
    }

    $logDirectory = Join-Path $PSScriptRoot ('logs/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Write-Host "Logs: $logDirectory"
    Write-Host 'Keep this window open. Press Ctrl+C to stop all services started here.'
    foreach ($service in $services) {
        Write-Host "Starting $($service.Module) on port $($service.Port)..."
        $module = $service.Module
        $escapedRoot = $PSScriptRoot.Replace("'", "''")
        $stripSecrets = if ($module -eq 'discovery-server' -or $module -eq 'api-gateway') {
            'Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:JWT_PRIVATE_KEY,Env:INTERNAL_SERVICE_TOKEN,Env:USER_SERVICE_INTERNAL_TOKEN -ErrorAction SilentlyContinue'
        } elseif ($module -ne 'user-service') {
            'Remove-Item Env:JWT_PRIVATE_KEY -ErrorAction SilentlyContinue'
        } else { '' }
        $command = "Set-Location -LiteralPath '$escapedRoot'; $stripSecrets; & '.\mvnw.cmd' -B -pl '$module' spring-boot:run '-Dspring-boot.run.arguments=--server.port=$($service.Port)'; exit `$LASTEXITCODE"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        $process = Start-Process powershell.exe -WindowStyle Hidden -PassThru -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encoded -RedirectStandardOutput (Join-Path $logDirectory "$module.log") -RedirectStandardError (Join-Path $logDirectory "$module.error.log")
        $started += @{ Process = $process; Module = $module }
        $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
        while ($true) {
            foreach ($entry in $started) {
                if ($entry.Process.HasExited) { throw "$($entry.Module) exited. Check its logs in $logDirectory" }
            }
            if (Test-ServiceReady $service) { break }
            if ((Get-Date) -ge $deadline) { throw "$module did not become ready within $StartupTimeoutSeconds seconds. Check its logs in $logDirectory" }
            Start-Sleep -Seconds 2
        }
        Write-Host "$module is ready."
    }
    Write-Host 'All five services are ready. Gateway: http://localhost:8080 | Eureka: http://localhost:8761'
    while ($true) {
        foreach ($entry in $started) {
            if ($entry.Process.HasExited) { throw "$($entry.Module) exited. Check its logs in $logDirectory" }
        }
        Start-Sleep -Seconds 2
    }
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    # Stop only process trees created by this invocation, in reverse order.
    for ($index = $started.Count - 1; $index -ge 0; $index--) {
        $entry = $started[$index]
        if (-not $entry.Process.HasExited) {
            Write-Host "Stopping $($entry.Module)..."
            & taskkill.exe /PID $entry.Process.Id /T /F 2>&1 | Out-Null
        }
    }
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}

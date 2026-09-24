[CmdletBinding()]
param(
    [ValidateRange(30, 3600)][int]$StartupTimeoutSeconds = 300,
    [switch]$Check,
    [Parameter(DontShow)][switch]$LoadJobSupportOnly
)

function Initialize-BackendJobSupport {
    if ('ContactTx.LauncherJob' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace ContactTx {
    // The launcher alone owns this non-inheritable handle. Windows closes it
    // even on forced termination, killing every process in the job hierarchy.
    public sealed class LauncherJob : IDisposable {
        private IntPtr handle;
        [StructLayout(LayoutKind.Sequential)] struct BasicLimits {
            public long ProcessTime, JobTime;
            public uint Flags;
            public UIntPtr MinWorkingSet, MaxWorkingSet;
            public uint ActiveProcesses;
            public UIntPtr Affinity;
            public uint Priority, Scheduling;
        }
        [StructLayout(LayoutKind.Sequential)] struct IoCounters {
            public ulong ReadOps, WriteOps, OtherOps, ReadBytes, WriteBytes, OtherBytes;
        }
        [StructLayout(LayoutKind.Sequential)] struct ExtendedLimits {
            public BasicLimits Basic;
            public IoCounters Io;
            public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)] struct StartupInfo {
            public int Size;
            public string Reserved, Desktop, Title;
            public uint X, Y, XSize, YSize, XCount, YCount, Fill, Flags;
            public ushort ShowWindow, ReservedSize;
            public IntPtr ReservedData, Input, Output, Error;
        }
        [StructLayout(LayoutKind.Sequential)] struct StartupInfoEx {
            public StartupInfo Info;
            public IntPtr Attributes;
        }
        [StructLayout(LayoutKind.Sequential)] struct ProcessInfo {
            public IntPtr Process, Thread;
            public uint ProcessId, ThreadId;
        }
        [StructLayout(LayoutKind.Sequential)] struct SecurityAttributes {
            public int Length;
            public IntPtr Descriptor;
            public int Inherit;
        }
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateFile(string name, uint access, uint share, ref SecurityAttributes security, uint creation, uint flags, IntPtr template);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool SetInformationJobObject(IntPtr job, int infoClass, ref ExtendedLimits info, uint length);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr OpenJobObject(uint access, bool inherit, string name);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool TerminateJobObject(IntPtr job, uint exitCode);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool InitializeProcThreadAttributeList(IntPtr list, int count, int flags, ref IntPtr size);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags, IntPtr attribute, IntPtr value, IntPtr size, IntPtr previous, IntPtr returned);
        [DllImport("kernel32.dll")]
        static extern void DeleteProcThreadAttributeList(IntPtr list);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool CreateProcess(string application, StringBuilder command, IntPtr processAttributes, IntPtr threadAttributes, bool inherit, uint flags, IntPtr environment, string directory, ref StartupInfoEx startup, out ProcessInfo process);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern uint ResumeThread(IntPtr thread);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool TerminateProcess(IntPtr process, uint exitCode);
        [DllImport("kernel32.dll")]
        static extern bool CloseHandle(IntPtr value);

        public LauncherJob(string name) {
            handle = CreateJobObject(IntPtr.Zero, name);
            if (handle == IntPtr.Zero) throw new Win32Exception();
            var limits = new ExtendedLimits();
            limits.Basic.Flags = 0x2000; // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            if (!SetInformationJobObject(handle, 9, ref limits, (uint)Marshal.SizeOf(limits))) {
                int error = Marshal.GetLastWin32Error();
                Dispose();
                throw new Win32Exception(error);
            }
        }

        public Process Start(string executable, string arguments, string directory) {
            return Start(executable, arguments, directory, null, null);
        }

        public Process Start(string executable, string arguments, string directory, string outputPath, string errorPath) {
            if (handle == IntPtr.Zero) throw new ObjectDisposedException("LauncherJob");
            IntPtr size = IntPtr.Zero, attributes = IntPtr.Zero, jobList = IntPtr.Zero;
            IntPtr handleList = IntPtr.Zero, input = IntPtr.Zero, output = IntPtr.Zero, errorOutput = IntPtr.Zero;
            bool initialized = false, resumed = false;
            var child = new ProcessInfo();
            try {
                InitializeProcThreadAttributeList(IntPtr.Zero, 2, 0, ref size);
                attributes = Marshal.AllocHGlobal(size);
                if (!InitializeProcThreadAttributeList(attributes, 2, 0, ref size)) throw new Win32Exception();
                initialized = true;
                jobList = Marshal.AllocHGlobal(IntPtr.Size);
                Marshal.WriteIntPtr(jobList, handle);
                // Windows 10+: assign atomically at creation. There is no window
                // where a launcher crash can leave an unowned suspended child.
                if (!UpdateProcThreadAttribute(attributes, 0, new IntPtr(0x2000D), jobList, new IntPtr(IntPtr.Size), IntPtr.Zero, IntPtr.Zero)) throw new Win32Exception();
                var security = new SecurityAttributes();
                security.Length = Marshal.SizeOf(security);
                security.Inherit = 1;
                input = CreateFile("NUL", 0x80000000, 3, ref security, 3, 0x80, IntPtr.Zero);
                output = CreateFile(outputPath ?? "NUL", 0x40000000, 3, ref security, outputPath == null ? 3u : 2u, 0x80, IntPtr.Zero);
                errorOutput = CreateFile(errorPath ?? "NUL", 0x40000000, 3, ref security, errorPath == null ? 3u : 2u, 0x80, IntPtr.Zero);
                if (input == new IntPtr(-1) || output == new IntPtr(-1) || errorOutput == new IntPtr(-1)) throw new Win32Exception();
                // Valid standard handles are needed by .NET/PowerShell when
                // spawning grandchildren without a console. Inherit only these
                // handles, never the job handle (which would defeat kill-on-close).
                handleList = Marshal.AllocHGlobal(3 * IntPtr.Size);
                Marshal.WriteIntPtr(handleList, 0, input);
                Marshal.WriteIntPtr(handleList, IntPtr.Size, output);
                Marshal.WriteIntPtr(handleList, 2 * IntPtr.Size, errorOutput);
                if (!UpdateProcThreadAttribute(attributes, 0, new IntPtr(0x20002), handleList, new IntPtr(3 * IntPtr.Size), IntPtr.Zero, IntPtr.Zero)) throw new Win32Exception();
                var startup = new StartupInfoEx();
                startup.Info.Size = Marshal.SizeOf(startup);
                startup.Info.Flags = 0x100; // STARTF_USESTDHANDLES
                startup.Info.Input = input;
                startup.Info.Output = output;
                startup.Info.Error = errorOutput;
                startup.Attributes = attributes;
                var command = new StringBuilder("\"" + executable + "\" " + arguments);
                // CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT | CREATE_SUSPENDED
                if (!CreateProcess(executable, command, IntPtr.Zero, IntPtr.Zero, true, 0x08080004, IntPtr.Zero, directory, ref startup, out child)) throw new Win32Exception();
                var process = Process.GetProcessById((int)child.ProcessId);
                // Pin the process handle so PID reuse cannot confuse monitoring.
                IntPtr pinnedHandle = process.Handle;
                if (ResumeThread(child.Thread) == uint.MaxValue) throw new Win32Exception();
                resumed = true;
                return process;
            } finally {
                if (!resumed && child.Process != IntPtr.Zero) TerminateProcess(child.Process, 1);
                if (child.Thread != IntPtr.Zero) CloseHandle(child.Thread);
                if (child.Process != IntPtr.Zero) CloseHandle(child.Process);
                if (initialized) DeleteProcThreadAttributeList(attributes);
                if (attributes != IntPtr.Zero) Marshal.FreeHGlobal(attributes);
                if (jobList != IntPtr.Zero) Marshal.FreeHGlobal(jobList);
                if (handleList != IntPtr.Zero) Marshal.FreeHGlobal(handleList);
                if (input != IntPtr.Zero && input != new IntPtr(-1)) CloseHandle(input);
                if (output != IntPtr.Zero && output != new IntPtr(-1)) CloseHandle(output);
                if (errorOutput != IntPtr.Zero && errorOutput != new IntPtr(-1)) CloseHandle(errorOutput);
            }
        }

        public static bool Stop(string name) {
            IntPtr job = OpenJobObject(0x0008, false, name); // JOB_OBJECT_TERMINATE
            if (job == IntPtr.Zero) {
                int error = Marshal.GetLastWin32Error();
                if (error == 2) return false; // Already gone.
                throw new Win32Exception(error);
            }
            try {
                if (!TerminateJobObject(job, 0)) throw new Win32Exception();
                return true;
            } finally { CloseHandle(job); }
        }

        public void Dispose() {
            if (handle != IntPtr.Zero) { CloseHandle(handle); handle = IntPtr.Zero; }
        }
    }
}
'@
}

function Get-BackendOccupiedPorts {
    param([int[]]$Ports)
    # Includes IPv4/IPv6 and wildcard listeners; never kills by port number.
    $listening = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    @($listening | Where-Object { $_.Port -in $Ports } | ForEach-Object { $_.Port } | Sort-Object -Unique)
}

# stop-all.ps1 imports only these helpers; no environment files are read.
if ($LoadJobSupportOnly) { return }

$ErrorActionPreference = 'Stop'
$services = @(
    @{ Module = 'discovery-server'; Port = 8761; App = $null },
    @{ Module = 'user-service'; Port = 8081; App = 'USER-SERVICE' },
    @{ Module = 'Contact_Service'; Port = 8082; App = 'CONTACT-SERVICE' },
    @{ Module = 'TransactionMicroservice'; Port = 8083; App = 'TRANSACTIONMICROSERVICE' },
    @{ Module = 'NotificationService'; Port = 8084; App = 'NOTIFICATION-SERVICE' },
    @{ Module = 'api-gateway'; Port = 8080; App = 'API-GATEWAY' }
)
$started = @()
$savedEnvironment = @{}
$job = $null
$stopSignal = $null
$launcherMutex = $null
$ownsMutex = $false
$statePath = Join-Path $PSScriptRoot '.backend-runtime.json'
$state = $null

function Save-BackendState {
    $temporaryPath = "$statePath.tmp"
    [IO.File]::WriteAllText($temporaryPath, ($state | ConvertTo-Json -Depth 5))
    if (Test-Path -LiteralPath $statePath) {
        [IO.File]::Replace($temporaryPath, $statePath, [NullString]::Value)
    } else {
        [IO.File]::Move($temporaryPath, $statePath)
    }
}

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
    # Serialize launchers for this checkout, including during preflight.
    $hash = [Security.Cryptography.SHA256]::Create()
    try { $rootHash = [BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($PSScriptRoot.ToLowerInvariant()))).Replace('-', '') }
    finally { $hash.Dispose() }
    $launcherMutex = New-Object Threading.Mutex($false, "Local\ContactTx-Launcher-$rootHash")
    try { $ownsMutex = $launcherMutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
    if (-not $ownsMutex) { throw 'This backend already has an active launcher. Use stop-all.bat first.' }
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

    # Use the shared schema only when no notification-specific database is set.
    $notificationDb = @('NOTIFICATION_DB_URL', 'NOTIFICATION_DB_USERNAME', 'NOTIFICATION_DB_PASSWORD')
    $configuredDb = @($notificationDb | Where-Object { -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
    if ($configuredDb.Count -eq 0) {
        foreach ($name in $notificationDb) {
            Set-LaunchEnvironment $name ([Environment]::GetEnvironmentVariable($name.Substring('NOTIFICATION_'.Length)))
        }
    } elseif ($configuredDb.Count -ne $notificationDb.Count) {
        throw 'Set all three NOTIFICATION_DB_URL, NOTIFICATION_DB_USERNAME and NOTIFICATION_DB_PASSWORD, or leave all three unset to use DB_*.'
    }

    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
    if (-not (Test-Path -LiteralPath $java)) { throw 'Java was not found. Set JAVA_HOME to your JDK 24 installation.' }
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'mvnw.cmd'))) { throw 'mvnw.cmd is missing.' }
    $occupied = @(Get-BackendOccupiedPorts ($services | ForEach-Object { $_.Port }))
    if ($occupied.Count) { throw "Ports already in use: $($occupied -join ', '). Use stop-all.bat for a managed launch. Older/unrelated processes must be stopped separately." }
    if ($Check) {
        Write-Host 'Preflight passed: environment, Java location, Maven wrapper, and ports checked. Database and Java version are not checked.'
        return
    }

    Initialize-BackendJobSupport
    $jobName = 'Local\ContactTx-' + [Guid]::NewGuid().ToString('N')
    $job = New-Object ContactTx.LauncherJob($jobName)
    $stopSignal = New-Object Threading.EventWaitHandle($false, ([Threading.EventResetMode]::ManualReset), "$jobName-Stop")
    $launcher = [Diagnostics.Process]::GetCurrentProcess()
    $state = @{
        Version = 1; Root = $PSScriptRoot; JobName = $jobName
        LauncherId = $PID; LauncherStartTicks = $launcher.StartTime.ToUniversalTime().Ticks.ToString()
        Ports = @($services | ForEach-Object { $_.Port }); Processes = @()
    }
    Save-BackendState

    $logDirectory = Join-Path $PSScriptRoot ('logs/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Write-Host "Logs: $logDirectory"
    Write-Host 'Close this window, press Ctrl+C, or run stop-all.bat to stop this backend.'
    Write-Host 'Kafka must already be running with the required topics. It is managed separately; see docs/run-backend-and-kafka.md.'
    foreach ($service in $services) {
        if ($stopSignal.WaitOne(0)) { throw 'Stop requested.' }
        Write-Host "Starting $($service.Module) on port $($service.Port)..."
        $module = $service.Module
        $escapedRoot = $PSScriptRoot.Replace("'", "''")
        $stripSecrets = if ($module -eq 'discovery-server' -or $module -eq 'api-gateway') {
            'Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:JWT_PRIVATE_KEY,Env:INTERNAL_SERVICE_TOKEN,Env:USER_SERVICE_INTERNAL_TOKEN -ErrorAction SilentlyContinue'
        } elseif ($module -eq 'NotificationService') {
            'Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:JWT_PRIVATE_KEY,Env:INTERNAL_SERVICE_TOKEN,Env:USER_SERVICE_INTERNAL_TOKEN -ErrorAction SilentlyContinue'
        } elseif ($module -ne 'user-service') {
            'Remove-Item Env:JWT_PRIVATE_KEY -ErrorAction SilentlyContinue'
        } else { '' }
        if ($module -ne 'NotificationService') {
            $stripSecrets += '; Remove-Item Env:NOTIFICATION_DB_URL,Env:NOTIFICATION_DB_USERNAME,Env:NOTIFICATION_DB_PASSWORD -ErrorAction SilentlyContinue'
        }
        $outputPath = Join-Path $logDirectory "$module.log"
        $errorPath = Join-Path $logDirectory "$module.error.log"
        $command = "Set-Location -LiteralPath '$escapedRoot'; $stripSecrets; & '.\mvnw.cmd' -B -pl '$module' spring-boot:run '-Dspring-boot.run.arguments=--server.port=$($service.Port)'; exit `$LASTEXITCODE"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        $shellPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
        $process = $job.Start($shellPath, "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encoded", $PSScriptRoot, $outputPath, $errorPath)
        $started += @{ Process = $process; Module = $module }
        $state.Processes += @{ Id = $process.Id; StartTicks = $process.StartTime.ToUniversalTime().Ticks.ToString(); Module = $module }
        Save-BackendState
        $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
        while ($true) {
            if ($stopSignal.WaitOne(0)) { throw 'Stop requested.' }
            foreach ($entry in $started) {
                if ($entry.Process.HasExited) { throw "$($entry.Module) exited. Check its logs in $logDirectory" }
            }
            if (Test-ServiceReady $service) { break }
            if ((Get-Date) -ge $deadline) { throw "$module did not become ready within $StartupTimeoutSeconds seconds. Check its logs in $logDirectory" }
            Start-Sleep -Seconds 2
        }
        Write-Host "$module is ready."
    }
    Write-Host 'All six services are ready. Gateway: http://localhost:8080 | Eureka: http://localhost:8761 | Notifications: http://localhost:8084'
    while ($true) {
        if ($stopSignal.WaitOne(0)) { throw 'Stop requested.' }
        foreach ($entry in $started) {
            if ($entry.Process.HasExited) { throw "$($entry.Module) exited. Check its logs in $logDirectory" }
        }
        Start-Sleep -Seconds 2
    }
} catch {
    if ($stopSignal -and $stopSignal.WaitOne(0)) {
        Write-Host 'Backend shutdown requested.'
    } else {
        Write-Host "Launcher failed: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
} finally {
    # Kernel-level cleanup remains effective even when this finally is bypassed.
    if ($job) { $job.Dispose() }
    try {
        if ($state) {
            $shutdownDeadline = (Get-Date).AddSeconds(10)
            do {
                $remainingPorts = @(Get-BackendOccupiedPorts $state.Ports)
                if (-not $remainingPorts.Count) { break }
                Start-Sleep -Milliseconds 200
            } while ((Get-Date) -lt $shutdownDeadline)
            if ($remainingPorts.Count) {
                Write-Warning "Still listening on ports: $($remainingPorts -join ', '). These may belong to other processes; no unrelated process was stopped."
            } else { Write-Host 'Backend stopped; all six service ports are free. Kafka is managed separately.' }
            if ((Test-Path -LiteralPath $statePath) -and ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).JobName -eq $state.JobName)) {
                Remove-Item -LiteralPath $statePath -Force
            }
        }
    } finally {
        if ($stopSignal) { $stopSignal.Dispose() }
        foreach ($entry in $started) { $entry.Process.Dispose() }
        foreach ($name in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
        if ($ownsMutex) { $launcherMutex.ReleaseMutex() }
        if ($launcherMutex) { $launcherMutex.Dispose() }
    }
}

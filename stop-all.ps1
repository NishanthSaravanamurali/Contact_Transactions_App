[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
try {
    . (Join-Path $PSScriptRoot 'start-all.ps1') -LoadJobSupportOnly
    $statePath = Join-Path $PSScriptRoot '.backend-runtime.json'
    $ports = @(8761, 8081, 8082, 8083, 8084, 8080)
    if (Test-Path -LiteralPath $statePath) {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($state.Version -ne 1 -or $state.Root -ne $PSScriptRoot -or $state.JobName -notmatch '^Local\\ContactTx-[a-f0-9]{32}$') {
            throw 'Invalid launcher state. Refusing to stop any process.'
        }
        $launcher = Get-Process -Id $state.LauncherId -ErrorAction SilentlyContinue
        if ($launcher) {
            try {
                # Pin the process identity before checking its creation time.
                $null = $launcher.Handle
                if ($launcher.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.LauncherStartTicks) {
                    Write-Host 'Recorded launcher has exited; its PID was reused. The new process will not be touched.'
                } else {
                    Initialize-BackendJobSupport
                    # Prevent the launcher from starting another service if this
                    # request arrives between two startup steps.
                    try {
                        $signal = [Threading.EventWaitHandle]::OpenExisting("$($state.JobName)-Stop")
                        try { $null = $signal.Set() } finally { $signal.Dispose() }
                    } catch [Threading.WaitHandleCannotBeOpenedException] {
                        # Launcher may have completed cleanup during this check.
                    }
                    if ([ContactTx.LauncherJob]::Stop($state.JobName)) {
                        Write-Host 'Stopped the recorded backend job and its child processes.'
                    } else { Write-Host 'The recorded backend job has already stopped.' }
                }
            } finally { $launcher.Dispose() }
        } else { Write-Host 'The recorded launcher has already exited.' }
    } else { Write-Host 'No managed backend launch is recorded.' }

    $deadline = (Get-Date).AddSeconds(10)
    do {
        $occupied = @(Get-BackendOccupiedPorts $ports)
        if (-not $occupied.Count) { break }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    if ($occupied.Count) {
        throw "Ports still occupied: $($occupied -join ', '). They may be from the old launcher or another app. No untracked process was stopped."
    }
    Write-Host 'All six backend ports are free. Kafka is managed separately.'
    # A forcibly closed launcher can leave harmless stale state; the next start
    # replaces it under the launcher's mutex. Do not race a new launch to delete it.
} catch {
    Write-Host "Stop failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

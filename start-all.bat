@echo off
setlocal
rem Starts all six Spring services; start Kafka first (see docs/run-backend-and-kafka.md).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
set "launcherExitCode=%errorlevel%"
if not "%launcherExitCode%"=="0" pause
exit /b %launcherExitCode%

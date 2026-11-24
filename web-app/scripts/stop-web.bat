@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0.."
set "PIDFILE=%CD%\config\server.pid"

if not exist "%PIDFILE%" (
  echo No running EquipLink Web server found via PID file.
  echo If it is still running, close the "EquipLink Web Server" console window.
  exit /b 0
)

set /p PID=<"%PIDFILE%"
if "!PID!"=="" (
  del "%PIDFILE%" >nul 2>&1
  echo No running EquipLink Web server found.
  exit /b 0
)

tasklist /FI "PID eq !PID!" 2>nul | findstr /I /C:"java.exe" >nul
if not errorlevel 1 (
  taskkill /PID !PID! /F >nul
  echo EquipLink Web server stopped.
) else (
  echo The recorded process was no longer running.
)
del "%PIDFILE%" >nul 2>&1

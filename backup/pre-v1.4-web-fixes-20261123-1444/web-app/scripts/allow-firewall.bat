@echo off
setlocal EnableExtensions
NET SESSION >nul 2>&1
if errorlevel 1 (
  echo RIGHT-CLICK this file and choose "Run as administrator" to open the firewall port.
  pause
  exit /b 1
)
netsh advfirewall firewall delete rule name="EquipLink Web (8080)" >nul 2>&1
netsh advfirewall firewall add rule name="EquipLink Web (8080)" dir=in action=allow protocol=TCP localport=8080 >nul
echo Windows Firewall rule added for TCP 8080.
pause

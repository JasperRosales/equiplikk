@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "WEB_HOME=%CD%"
set "ROOT=%WEB_HOME%\.."

if not exist "backend\build\classes\web\main\WebServer.class" (
  call "%WEB_HOME%\backend\build-web.bat"
  if errorlevel 1 exit /b 1
)

echo Starting EquipLink Web at http://localhost:8080 ...
echo LAN address is printed in the server console.
start "EquipLink Web Server" java -Dequiplink.web.home="%WEB_HOME%" -cp "%WEB_HOME%\backend\build\classes;%ROOT%\lib\*" web.main.WebServer

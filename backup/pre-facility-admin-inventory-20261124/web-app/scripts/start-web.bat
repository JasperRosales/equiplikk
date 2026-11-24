@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "WEB_HOME=%CD%"
set "ROOT=%WEB_HOME%\.."

REM Always compile so backend fixes cannot be hidden by stale class files.
call "%WEB_HOME%\backend\build-web.bat"
if errorlevel 1 exit /b 1

echo Starting EquipLink Web at http://localhost:8080 ...
echo LAN address is printed in the server console.
start "EquipLink Web Server" java -Dequiplink.web.home="%WEB_HOME%" -cp "%WEB_HOME%\backend\build\classes;%ROOT%\lib\*" web.main.WebServer

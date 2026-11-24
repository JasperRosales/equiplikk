@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "WEB_HOME=%~dp0.."
set "ROOT=%~dp0..\.."
set "OUT=%~dp0build\classes"
set "SOURCES=%TEMP%\equiplink-web-sources.txt"

if not exist "%OUT%" mkdir "%OUT%"

REM Compile the EXISTING desktop sources together with the new web sources.
REM This reuses the exact same service/business-logic classes; nothing in src\ is changed.
dir /s /b "%ROOT%\src\*.java" > "%SOURCES%"
dir /s /b "%~dp0src\*.java" >> "%SOURCES%"

echo Compiling EquipLink desktop + web sources ...
javac -encoding UTF-8 -nowarn -cp "%ROOT%\lib\*" -d "%OUT%" @"%SOURCES%"
if errorlevel 1 (
  echo ERROR: compilation failed.
  exit /b 1
)

echo Web backend compiled to %OUT%

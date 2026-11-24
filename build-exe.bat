@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

REM ============================================================
REM  EquipLink - build the Windows MSI installer
REM  Usage: build-exe.bat [version]
REM    version : e.g. 1.4.0  (default: 1.4.0)
REM  Output: build\release\dist\EquipLink-<version>.msi
REM ============================================================

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=1.4.0"

set "ROOT=%~dp0"
set "BUILD=%ROOT%build\release"
set "WIX_DIR=%LOCALAPPDATA%\Temp\opencode\wix314"
set "WIX_ZIP=%LOCALAPPDATA%\Temp\opencode\wix314-binaries.zip"
set "REPO=Sarah-EM09/EQUIPLINK"
set "UPGRADE_UUID=ae55204b-945a-4508-8ae8-111fb443d77b"

echo.
echo === Building EquipLink %VERSION% MSI installer ===
echo.

where javac >nul 2>&1
if errorlevel 1 (echo ERROR: javac not found. Install JDK 17 and add it to PATH. & exit /b 1)
where jpackage >nul 2>&1
if errorlevel 1 (echo ERROR: jpackage not found. Requires JDK 14 or newer. & exit /b 1)

REM --- WiX 3.14 (required by jpackage to build an MSI) ---
if not exist "%WIX_DIR%\candle.exe" (
  echo Downloading WiX 3.14 ...
  if not exist "%WIX_DIR%" mkdir "%WIX_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile '%WIX_ZIP%' -UseBasicParsing"
  if errorlevel 1 (echo ERROR: WiX download failed. & exit /b 1)
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%WIX_ZIP%' -DestinationPath '%WIX_DIR%' -Force"
  if errorlevel 1 (echo ERROR: WiX extraction failed. & exit /b 1)
)
set "PATH=%WIX_DIR%;%PATH%"

if not exist "%BUILD%" mkdir "%BUILD%"
if not exist "%BUILD%\classes" mkdir "%BUILD%\classes"
if not exist "%BUILD%\input" mkdir "%BUILD%\input"
if not exist "%BUILD%\dist" mkdir "%BUILD%\dist"

REM --- Compile sources into the jar ---
echo Compiling Java sources ...
dir /s /b "%ROOT%src\*.java" > "%BUILD%\sources.txt"
javac -encoding UTF-8 -nowarn -cp "%ROOT%lib\*" -d "%BUILD%\classes" @"%BUILD%\sources.txt"
if errorlevel 1 (echo ERROR: compilation failed. & exit /b 1)

xcopy /E /I /Y /Q "%ROOT%src\assets\*" "%BUILD%\classes\src\assets" >nul
copy /Y "%ROOT%room.md" "%BUILD%\classes\room.md" >nul
> "%BUILD%\classes\equiplink.properties" echo version=%VERSION%
>> "%BUILD%\classes\equiplink.properties" echo repo=%REPO%

set "CP="
for %%f in ("%ROOT%lib\*.jar") do (
  if defined CP (set "CP=!CP! %%~nxf") else (set "CP=%%~nxf")
)
> "%BUILD%\manifest.txt" echo Main-Class: src.Main
>> "%BUILD%\manifest.txt" echo Class-Path: %CP%

jar cfm "%BUILD%\input\equiplink.jar" "%BUILD%\manifest.txt" -C "%BUILD%\classes" .
if errorlevel 1 (echo ERROR: jar creation failed. & exit /b 1)
copy /Y "%ROOT%lib\*.jar" "%BUILD%\input\" >nul

REM --- MSI installer ---
echo Building MSI installer ...
if exist "%BUILD%\dist\EquipLink-%VERSION%.msi" del /f /q "%BUILD%\dist\EquipLink-%VERSION%.msi"
jpackage --type msi --name EquipLink --app-version %VERSION% --vendor EquipLink --description "Campus equipment borrowing system" --input "%BUILD%\input" --main-jar equiplink.jar --main-class src.Main --icon "%ROOT%src\assets\equiplink.ico" --win-per-user-install --win-dir-chooser --win-menu --win-menu-group EquipLink --win-shortcut --win-upgrade-uuid %UPGRADE_UUID% --dest "%BUILD%\dist"
if errorlevel 1 (echo ERROR: jpackage MSI build failed. & exit /b 1)

echo.
echo MSI installer created: %BUILD%\dist\EquipLink-%VERSION%.msi
echo.
echo === Done ===
if /i not "%NOPAUSE%"=="1" pause

@echo off
setlocal
set GRADLE_VERSION=9.2.0
set WRAPPER_DIR=%~dp0.gradle\wrapper
set GRADLE_HOME=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat

if exist "%GRADLE_BIN%" goto run

if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
echo Downloading Gradle %GRADLE_VERSION% directly...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip' '%WRAPPER_DIR%'"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_BIN%" %*

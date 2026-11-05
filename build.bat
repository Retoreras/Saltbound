@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 not found. Install JDK 21 from https://adoptium.net and run again.
  pause & exit /b 1
)
set GRADLE_VER=8.12
if not exist ".gradle-dist\gradle-%GRADLE_VER%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VER% ...
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip -OutFile gradle.zip; Expand-Archive gradle.zip .gradle-dist -Force; Remove-Item gradle.zip"
)
set TASK=%1
if "%TASK%"=="" set TASK=build
call ".gradle-dist\gradle-%GRADLE_VER%\bin\gradle.bat" %TASK%
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the red error text and send it to Claude.
  pause & exit /b 1
)
for /f "tokens=2 delims==" %%v in ('findstr /b "mod_version" gradle.properties') do set VER=%%v
if exist "build\libs\saltbound-%VER%.jar" (
  del /Q saltbound-*.jar >nul 2>nul
  copy /Y "build\libs\saltbound-%VER%.jar" "saltbound-%VER%.jar" >nul
)
echo.
if exist "saltbound-%VER%.jar" (echo DONE. Mod file: saltbound-%VER%.jar, next to this script) else (echo DONE.)
pause

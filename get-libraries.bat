@echo off
rem Downloads the third-party jars ChronoCase needs into lib\.
rem Run once after cloning (or whenever lib\ is empty).
rem curl.exe ships with Windows 10/11.
rem JavaFX publishes per-platform classifier jars; on Windows that is
rem -win. The script saves them under the plain artifact name so
rem build.bat / run-gui.bat work unchanged.
rem Already-downloaded (and size-checked) jars are skipped.
cd /d "%~dp0"
setlocal enabledelayedexpansion
if not exist lib mkdir lib

set BASE=https://repo1.maven.org/maven2
set CLASSIFIER=win

call :fetch com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar lib\mysql-connector-j-9.4.0.jar 100000 || goto :fail
call :fetch org/openjfx/javafx-base/26/javafx-base-26-%CLASSIFIER.jar lib\javafx-base-26.jar 10000 || goto :fail
call :fetch org/openjfx/javafx-graphics/26/javafx-graphics-26-%CLASSIFIER.jar lib\javafx-graphics-26.jar 10000 || goto :fail
call :fetch org/openjfx/javafx-controls/26/javafx-controls-26-%CLASSIFIER.jar lib\javafx-controls-26.jar 10000 || goto :fail

echo All libraries ready in lib\.
exit /b 0

:fetch
rem %1 = maven path, %2 = target file, %3 = min size in bytes
if exist %2 (
    for %%F in (%2) do set SIZE=%%~zF
    if !SIZE! GEQ %3 (
        echo already present: %2
        exit /b 0
    )
    echo %2 is too small (!SIZE! bytes^) - re-downloading...
)
echo downloading %2 ...
curl -fL --retry 3 -o %2 %BASE%/%1 || exit /b 1
for %%F in (%2) do set SIZE=%%~zF
if !SIZE! LSS %3 (
    echo ERROR: %2 is too small (!SIZE! bytes^) - download failed
    exit /b 1
)
exit /b 0

:fail
echo DOWNLOAD FAILED - check your internet connection and retry.
pause
exit /b 1

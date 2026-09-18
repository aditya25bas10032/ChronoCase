@echo off
rem Runs the ChronoCase GUI on Windows (cmd / double-click).
rem ChronoCaseLauncher lets JavaFX run from the plain classpath.
rem
rem Double-click friendly: picks a modern JDK itself (see build.bat) -
rem the Oracle Java 8 shim that usually sits first in the Windows PATH
rem cannot run this project and would otherwise flash an error away.
cd /d "%~dp0"
if not exist out\view\ChronoCaseLauncher.class (
    echo Not built yet - running build.bat first...
    call build.bat || exit /b 1
)

rem ---- resolve a JDK 9+ toolchain (same logic as build.bat) ----
set "JAVA_EXE="
java --version >nul 2>&1
if not errorlevel 1 (
    set "JAVA_EXE=java"
)
if not defined JAVA_EXE if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" --version >nul 2>nul
    if not errorlevel 1 set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE for /d %%D in ("%USERPROFILE%\.jdks\*") do (
    if not defined JAVA_EXE if exist "%%D\bin\java.exe" (
        "%%D\bin\java.exe" --version >nul 2>nul
        if not errorlevel 1 set "JAVA_EXE=%%D\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    echo ERROR: no modern JDK found. Install JDK 17+ or set JAVA_HOME.
    pause
    exit /b 1
)

"%JAVA_EXE%" -cp "out;lib/javafx-base-26.jar;lib/javafx-graphics-26.jar;lib/javafx-controls-26.jar" view.ChronoCaseLauncher %*
if errorlevel 1 pause

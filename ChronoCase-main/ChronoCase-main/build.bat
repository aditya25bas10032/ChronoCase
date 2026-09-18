@echo off
rem Compiles ChronoCase into out\ (JavaFX + MySQL jars from lib\).
rem Run get-libraries.bat once before this.
rem
rem Double-click friendly: Windows often has the old Oracle Java 8 shim
rem (java8path/javapath) FIRST in the system PATH, so plain "javac" can be
rem Java 8. This script therefore picks a modern JDK (9+) itself:
rem   1. "java" on PATH          (used only if it is Java 9+)
rem   2. %JAVA_HOME%             (if it contains a JDK 9+)
rem   3. %USERPROFILE%\.jdks\*   (IntelliJ-downloaded JDKs, any that is 9+)
cd /d "%~dp0"
if not exist lib\javafx-controls-26.jar (
    echo Libraries missing - run get-libraries.bat first.
    pause
    exit /b 1
)

rem ---- resolve a JDK 9+ toolchain ----
set "JAVA_EXE="
set "JAVAC_EXE="
java --version >nul 2>&1
if not errorlevel 1 (
    rem "java --version" only exists on Java 9+, so this java is modern enough
    set "JAVA_EXE=java"
    set "JAVAC_EXE=javac"
)
if not defined JAVA_EXE if exist "%JAVA_HOME%\bin\javac.exe" (
    "%JAVA_HOME%\bin\java.exe" --version >nul 2>&1
    if not errorlevel 1 (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    )
)
if not defined JAVA_EXE for /d %%D in ("%USERPROFILE%\.jdks\*") do (
    if not defined JAVA_EXE if exist "%%D\bin\javac.exe" (
        "%%D\bin\java.exe" --version >nul 2>&1
        if not errorlevel 1 (
            set "JAVA_EXE=%%D\bin\java.exe"
            set "JAVAC_EXE=%%D\bin\javac.exe"
        )
    )
)
if not defined JAVA_EXE (
    echo ERROR: no modern JDK found. Install JDK 17+ or set JAVA_HOME.
    pause
    exit /b 1
)
echo Using JDK: %JAVAC_EXE%

if exist out rmdir /s /q out
mkdir out\view

"%JAVAC_EXE%" -d out -cp "lib\javafx-base-26.jar;lib\javafx-graphics-26.jar;lib\javafx-controls-26.jar;lib\mysql-connector-j-9.4.0.jar" src\*.java src\model\*.java src\service\*.java src\io\*.java src\database\*.java src\controller\*.java src\view\*.java || goto :fail

copy /y src\view\styles.css out\view\styles.css >nul || goto :fail

echo BUILD OK - run the GUI with run-gui.bat
exit /b 0

:fail
echo BUILD FAILED - see the javac errors above.
pause
exit /b 1

@echo off
REM Builds the SSTV app into a real executable.
REM
REM   build.bat            -> compiles + builds sstv.jar (run anywhere with: java -jar sstv.jar)
REM   build.bat package    -> also builds a native SSTV.exe (in dist\SSTV\) via jpackage,
REM                           with no separate Java install required to run it.
REM
REM Requires a full JDK (17+) on PATH -- i.e. javac and jar must work.
REM The native-app step additionally requires jpackage (bundled with JDK 16+).

setlocal enabledelayedexpansion
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo error: javac not found on PATH. Install a JDK ^(17+^) and try again.
    echo        ^(a JRE is not enough -- you need the full JDK, which includes javac.^)
    exit /b 1
)

echo == Compiling ==
if exist out rmdir /s /q out
mkdir out

set SOURCES=
for /r src %%f in (*.java) do set SOURCES=!SOURCES! "%%f"
javac -d out !SOURCES!
if errorlevel 1 exit /b 1

echo == Building sstv.jar ==
if exist sstv.jar del sstv.jar
jar cfe sstv.jar com.sstv.Main -C out .
echo Built sstv.jar
echo   Run it with: java -jar sstv.jar

if "%1"=="package" (
    where jpackage >nul 2>nul
    if errorlevel 1 (
        echo error: jpackage not found on PATH. It ships with JDK 16+; update your JDK to use "package".
        exit /b 1
    )

    echo == Building native SSTV.exe with jpackage ==
    if exist build\jarinput rmdir /s /q build\jarinput
    if exist dist rmdir /s /q dist
    mkdir build\jarinput
    copy sstv.jar build\jarinput\ >nul

    jpackage ^
        --type app-image ^
        --input build\jarinput ^
        --dest dist ^
        --name SSTV ^
        --main-jar sstv.jar ^
        --main-class com.sstv.Main ^
        --description "SSTV Encoder/Decoder (Martin M1)" ^
        --vendor "Prototype"

    echo Built dist\SSTV\SSTV.exe -- double-click it to run.
    echo This is a standalone app: it bundles its own Java runtime, so it runs without
    echo anyone else needing Java installed.
)

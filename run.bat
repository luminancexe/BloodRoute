@echo off
title BloodRoute - Emergency Blood Matching and Routing System
echo =====================================================================
echo  🩸 Compiling and Launching BloodRoute (BIC2214 DSA Project)
echo =====================================================================

if not exist bin mkdir bin

echo [1/2] Compiling Java code...
javac -d bin src/bloodroute/model/*.java src/bloodroute/ds/*.java src/bloodroute/algorithm/*.java src/bloodroute/service/*.java src/bloodroute/server/*.java src/bloodroute/*.java test/bloodroute/*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed! Please check your JDK installation.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/2] Launching BloodRoute...
echo.
java -cp bin bloodroute.Main
pause

@echo off
title BloodRoute - Automated Test Suite
echo =====================================================================
echo  🧪 Compiling and Running BloodRoute Test Suite (BIC2214 DSA Project)
echo =====================================================================

if not exist bin mkdir bin

echo [1/2] Compiling source and test files...
javac -d bin src/bloodroute/model/*.java src/bloodroute/ds/*.java src/bloodroute/algorithm/*.java src/bloodroute/service/*.java src/bloodroute/server/*.java src/bloodroute/*.java test/bloodroute/*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed! Please check your JDK installation.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/2] Running automated test suite...
echo.
java -cp bin test.bloodroute.BloodRouteTestSuite
pause

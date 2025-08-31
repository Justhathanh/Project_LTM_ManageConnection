@echo off
echo Testing TLS Configuration...
echo.

cd /d "%~dp0"

echo Compiling TLS test...
javac -cp "target/classes" src/test/java/com/wifiguard/server/TlsTest.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Running TLS test...
java -cp "target/classes;src/test/java" com.wifiguard.server.TlsTest

echo.
echo Test completed.
pause

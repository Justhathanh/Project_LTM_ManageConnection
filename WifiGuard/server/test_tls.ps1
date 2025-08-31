Write-Host "Testing TLS Configuration..." -ForegroundColor Green
Write-Host ""

# Change to script directory
Set-Location $PSScriptRoot

Write-Host "Compiling TLS test..." -ForegroundColor Yellow
javac -cp "target/classes" src/test/java/com/wifiguard/server/TlsTest.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    Read-Host "Press Enter to continue"
    exit 1
}

Write-Host "Running TLS test..." -ForegroundColor Yellow
java -cp "target/classes;src/test/java" com.wifiguard.server.TlsTest

Write-Host ""
Write-Host "Test completed." -ForegroundColor Green
Read-Host "Press Enter to continue"

@echo off
cd /d "%~dp0"
java --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar %*
if errorlevel 1 (
    echo.
    echo Failed to start. Install JDK 17+ and make sure "java" is on your PATH.
    pause
)

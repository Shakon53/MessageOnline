@echo off
echo ==========================================
echo  MessageOnline Admin Panel
echo ==========================================
set NODE_OPTIONS=--openssl-legacy-provider

:: Kill any existing instance on port 3001
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001 "') do (
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 1 /nobreak >nul

echo Starting...
node server.js
pause

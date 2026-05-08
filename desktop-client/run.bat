@echo off
echo =============================================
echo   MessageOnline Desktop Client — Запуск
echo =============================================
echo.

where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Maven не найден!
    pause
    exit /b 1
)

echo [INFO] Запуск Desktop клиента...
call mvn javafx:run

pause

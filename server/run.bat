@echo off
echo =============================================
echo   MessageOnline Chat Server — Запуск
echo =============================================
echo.

:: Проверяем наличие Maven
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Maven не найден! Установите Maven: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

:: Проверяем наличие Java 17+
java -version 2>&1 | findstr "version" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Java не найдена! Установите JDK 17+: https://adoptium.net/
    pause
    exit /b 1
)

echo [INFO] Сборка проекта...
call mvn package -q
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Сборка не удалась!
    pause
    exit /b 1
)

echo [INFO] Запуск сервера на порту 8888...
echo [INFO] Для остановки нажмите Ctrl+C
echo.
java -jar target\chat-server.jar %1

pause

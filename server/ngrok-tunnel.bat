@echo off
echo ╔══════════════════════════════════════════════╗
echo ║   MessageOnline — Быстрый туннель ngrok      ║
echo ║   Работает без деплоя, пока открыт терминал  ║
echo ╚══════════════════════════════════════════════╝
echo.
echo Используй ngrok если хочешь быстро протестировать
echo без регистрации на облаке.
echo.

:: Проверяем ngrok
where ngrok >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [!] ngrok не установлен.
    echo.
    echo Установка:
    echo   1. Скачай: https://ngrok.com/download
    echo   2. Зарегистрируйся бесплатно: https://dashboard.ngrok.com/signup
    echo   3. Скопируй authtoken с: https://dashboard.ngrok.com/get-started/your-authtoken
    echo   4. Выполни: ngrok config add-authtoken ТВОЙ_ТОКЕН
    echo.
    pause
    exit /b 1
)

:: Сначала запускаем сервер в фоне
echo [1/2] Запускаем Chat Server на порту 8888...
if not exist "target\chat-server.jar" (
    echo Собираем JAR...
    call mvn package -q
)
start "MessageOnline Server" java -jar target\chat-server.jar
timeout /t 2 /nobreak >nul

:: Потом запускаем ngrok туннель
echo [2/2] Открываем туннель ngrok...
echo.
echo После запуска скопируй адрес вида:  tcp://X.tcp.eu.ngrok.io:XXXXX
echo И укажи его в приложении или в ServerConfig.kt
echo.
echo ┌─────────────────────────────────────────────────────────┐
echo │  Хост: X.tcp.eu.ngrok.io  (из поля "Forwarding")       │
echo │  Порт: XXXXX              (число после последнего :)    │
echo └─────────────────────────────────────────────────────────┘
echo.
ngrok tcp 8888

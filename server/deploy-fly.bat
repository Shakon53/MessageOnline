@echo off
echo ╔══════════════════════════════════════════════╗
echo ║   MessageOnline — Деплой на Fly.io           ║
echo ║   Бесплатный хостинг для TCP сервера         ║
echo ╚══════════════════════════════════════════════╝
echo.
echo [Шаг 1/5] Проверяем flyctl...
where flyctl >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo flyctl не найден. Устанавливаем...
    powershell -Command "iwr https://fly.io/install.ps1 -useb | iex"
    if %ERRORLEVEL% NEQ 0 (
        echo [ОШИБКА] Установка не удалась. Скачай вручную: https://fly.io/docs/hands-on/install-flyctl/
        pause & exit /b 1
    )
)
echo OK — flyctl установлен

echo.
echo [Шаг 2/5] Входим в аккаунт Fly.io...
echo (Если нет аккаунта — откроется браузер для регистрации, это бесплатно)
flyctl auth login

echo.
echo [Шаг 3/5] Создаём приложение...
echo Введи уникальное имя (например: messageonline-ivan или messageonline-2024):
set /p APP_NAME="Имя приложения: "

flyctl apps create %APP_NAME% --machines

echo.
echo [Шаг 4/5] Создаём постоянное хранилище для базы данных...
flyctl volumes create chat_data --app %APP_NAME% --region fra --size 1

echo.
echo Обновляем fly.toml с именем приложения...
powershell -Command "(Get-Content fly.toml) -replace 'app = ""messageonline-chat""', 'app = ""%APP_NAME%""' | Set-Content fly.toml"

echo.
echo [Шаг 5/5] Деплоим сервер...
flyctl deploy --app %APP_NAME%

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║  ГОТОВО! Твой сервер доступен в интернете:                  ║
echo ║                                                              ║
echo ║  Хост: %APP_NAME%.fly.dev
echo ║  Порт: 8888                                                  ║
echo ║                                                              ║
echo ║  Теперь открой файл:                                         ║
echo ║  android-client/.../network/ServerConfig.kt                  ║
echo ║  и измени HOST на: %APP_NAME%.fly.dev
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo Статус сервера:
flyctl status --app %APP_NAME%

echo.
echo Логи сервера (Ctrl+C для выхода):
flyctl logs --app %APP_NAME%

pause

@echo off
chcp 65001 >nul
cls

echo.
echo  ============================================================
echo   MessageOnline - Deploy na Railway cherez GitHub
echo   Skript sdelaet vsyo avtomaticheski!
echo  ============================================================
echo.

:: ============================================================
:: SHAG 1: Proveryaem Git
:: ============================================================
echo  [1/6] Proveryaem Git...
where git >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [OSHIBKA] Git ne ustanovlen!
    echo  Skachai: https://git-scm.com/download/win
    echo  Posle ustanovki perezapusti etot skript.
    pause & exit /b 1
)
echo  OK - Git naiden
echo.

:: ============================================================
:: SHAG 2: Proveryaem GitHub CLI
:: ============================================================
echo  [2/6] Proveryaem GitHub CLI (gh)...
where gh >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo  GitHub CLI ne naiden. Ustanavlivaem cherez winget...
    winget install --id GitHub.cli -e --silent
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo  [!] Ustanovka cherez winget ne udalas.
        echo  Skachai vruchnuyu: https://cli.github.com/
        echo  Ustanovi, perezapusti terminal i zapusti skript snova.
        pause & exit /b 1
    )
    set "PATH=%PATH%;%LOCALAPPDATA%\Microsoft\WinGet\Packages\GitHub.cli_Microsoft.Winget.Source_8wekyb3d8bbwe\tools"
)
echo  OK - GitHub CLI naiden
echo.

:: ============================================================
:: SHAG 3: Vkhod v GitHub
:: ============================================================
echo  [3/6] Proveryaem avtorizatsiyu v GitHub...
gh auth status >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo  Nuzhno voyti v GitHub akkaunt.
    echo  Otkroyetsya brauzer - sledui instruktsiyam:
    echo.
    gh auth login
    if %ERRORLEVEL% NEQ 0 (
        echo  [OSHIBKA] Avtorizatsiya ne udalas
        pause & exit /b 1
    )
)
echo  OK - avtorizovan v GitHub
echo.

:: ============================================================
:: SHAG 4: Sozdayom repozitoriy i pushim kod
:: ============================================================
echo  [4/6] Initsializiruem Git repozitoriy...
echo.

if not exist ".git" (
    git init
    git branch -M main
)

set /p REPO_NAME="  Vvedi nazvaniye repozitoriya GitHub (naprimer: MessageOnline): "
if "%REPO_NAME%"=="" set REPO_NAME=MessageOnline

git add .
git status --short

echo.
echo  Sozdayom kommit...
git commit -m "Initial commit: MessageOnline chat app" 2>nul || (
    git commit --allow-empty -m "Initial commit: MessageOnline chat app"
)

echo.
echo  Sozdayom repozitoriy na GitHub i zagruzhaem kod...
gh repo create %REPO_NAME% --public --push --source=. --remote=origin

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [!] Repozitoriy uzhe sushchestvuyet ili oshibka. Probuyem prosto pushnut...
    git remote remove origin 2>nul
    for /f %%i in ('gh api user -q ".login"') do set GH_USER=%%i
    git remote add origin https://github.com/%GH_USER%/%REPO_NAME%.git
    git push -u origin main --force
)

echo.
echo  OK - Kod zagruzhen na GitHub!

for /f %%i in ('gh api user -q ".login"') do set GH_USER=%%i
set REPO_URL=https://github.com/%GH_USER%/%REPO_NAME%
echo  Repozitoriy: %REPO_URL%
echo.

:: ============================================================
:: SHAG 5: Instruktsiya po Railway
:: ============================================================
echo  [5/6] Otkryvayem Railway...
echo.
echo  ============================================================
echo   SDELAY SLEDUYUSHCHIYE SHAGI V BRAUZERYE:
echo  ============================================================
echo.
echo   -- Sozdayom proyekt --
echo   1. Nazhmи "New Project"
echo   2. Vyberi "Deploy from GitHub repo"
echo   3. Naidi repozitoriy: %REPO_NAME%
echo   4. Nazhmи "Add variables" i dobav:
echo        ROOT_DIRECTORY = server
echo   5. Nazhmи Deploy -^> podozhdи ~2 minuty (sborka Docker)
echo.
echo   -- Posle uspeshnogo deploya --
echo   6. Klikni na svoy servis (sinyaya kartochka)
echo   7. Pereydi vo vkladku "Settings"
echo   8. Razdel "Networking" -^> nazhmи "Add TCP Proxy"
echo      Internal Port: 8888 -^> nazhmи "Add"
echo   9. Skopiruy adres vida:
echo        roundhouse.proxy.rlwy.net : 31234
echo.
echo   -- Baza dannykh (chtoby soobshcheniya ne teryalis) --
echo   10. V tom zhe servise pereydi vo vkladku "Volumes"
echo   11. Nazhmи "New Volume"
echo   12. Mount Path: /data -^> nazhmи "Add"
echo   13. Railway avtomaticheski perezapustit server s diskom
echo.
echo  ============================================================
echo.
echo  Nazhmи ENTER chtoby otkryt Railway v brauzerye...
pause >nul

start https://railway.app/new

echo.
echo  Ozhidai poka vypolnish vse shagi v brauzerye...
echo  Kogda poluchish adres servera - vernis syuda.
echo.
pause

:: ============================================================
:: SHAG 6: Obnovlyayem ServerConfig.kt
:: ============================================================
echo  [6/6] Obnovlyayem adres servera v prilozhenii...
echo.
echo  Vvedi adres kotoryy vydal Railway:
echo.
set /p RAILWAY_HOST="  Khost ot Railway (naprimer: roundhouse.proxy.rlwy.net): "
set /p RAILWAY_PORT="  Port ot Railway (naprimer: 31234): "

if "%RAILWAY_HOST%"=="" goto :skip_config
if "%RAILWAY_PORT%"=="" goto :skip_config

set CONFIG_FILE=android-client\app\src\main\java\com\messageonline\android\network\ServerConfig.kt

powershell -Command "(Get-Content '%CONFIG_FILE%') -replace 'const val HOST.*', 'const val HOST: String = \"%RAILWAY_HOST%\"' | Set-Content '%CONFIG_FILE%'"
powershell -Command "(Get-Content '%CONFIG_FILE%') -replace 'const val PORT.*= [0-9]+', 'const val PORT: Int    = %RAILWAY_PORT%' | Set-Content '%CONFIG_FILE%'"

echo.
echo  OK - ServerConfig.kt obnovlen!
echo    HOST = %RAILWAY_HOST%
echo    PORT = %RAILWAY_PORT%

git add android-client\app\src\main\java\com\messageonline\android\network\ServerConfig.kt
git commit -m "Set Railway server address: %RAILWAY_HOST%:%RAILWAY_PORT%"
git push origin main

echo.
echo  Obnovleniye pushnuty na GitHub.
goto :done

:skip_config
echo  [!] Adres ne vveden - ServerConfig.kt ne obnovlen.
echo  Obnovi vruchnuyu: android-client\...\network\ServerConfig.kt

:done
echo.
echo  ============================================================
echo   GOTOVO! Sleduyushchiye shagi:
echo.
echo   1. Otkroy Android Studio
echo      Build -^> Rebuild Project
echo.
echo   2. Ustanovи obnovlennyy APK na telefon
echo.
echo   3. V prilozhenii adres budet zapolnen avtomaticheski!
echo.
echo   4. Podklyuchaytes s LYUBOGO telefona v mirye!
echo  ============================================================
echo.
if not "%RAILWAY_HOST%"=="" echo  Tvoy server: %RAILWAY_HOST%:%RAILWAY_PORT%
echo  Repozitoriy: %REPO_URL%
echo.
pause

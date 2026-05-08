# 🌐 Деплой MessageOnline в интернет

Чтобы приложение работало как WhatsApp — с любого телефона, без Wi-Fi — нужно разместить сервер в облаке. Ниже три варианта от простого к надёжному, **все бесплатные**.

---

## 🥇 Вариант 1: Fly.io — лучший выбор (навсегда бесплатно)

**Плюсы:** надёжно, TCP поддерживается, данные не теряются, постоянный адрес  
**Минусы:** нужно установить одну утилиту

### Шаг 1 — Установи flyctl

**Windows (PowerShell от имени администратора):**
```powershell
iwr https://fly.io/install.ps1 -useb | iex
```

**Linux/macOS:**
```bash
curl -L https://fly.io/install.sh | sh
```

### Шаг 2 — Зарегистрируйся и войди

```bash
flyctl auth signup    # Регистрация (бесплатно, карта НЕ нужна)
# или
flyctl auth login     # Если уже есть аккаунт
```

### Шаг 3 — Запусти автоматический деплой (Windows)

```
cd server
deploy-fly.bat
```

**Или вручную (любая ОС):**

```bash
cd server

# Создаём приложение (имя должно быть уникальным во всём Fly.io)
flyctl apps create messageonline-ivan --machines

# Создаём постоянный диск для SQLite базы данных
flyctl volumes create chat_data --app messageonline-ivan --region fra --size 1

# Деплоим (Docker образ собирается автоматически)
flyctl deploy --app messageonline-ivan
```

### Шаг 4 — Настрой приложения

После деплоя твой сервер будет доступен по адресу:
```
messageonline-ivan.fly.dev : 8888
```

Открой файл `android-client/app/src/main/java/.../network/ServerConfig.kt`:
```kotlin
object ServerConfig {
    const val HOST = "messageonline-ivan.fly.dev"  // ← СЮДА
    const val PORT = 8888
}
```

### Полезные команды Fly.io

```bash
flyctl status --app messageonline-ivan      # Статус сервера
flyctl logs --app messageonline-ivan        # Логи в реальном времени
flyctl restart --app messageonline-ivan     # Перезапустить
flyctl deploy --app messageonline-ivan      # Обновить после изменений в коде
```

---

## 🥈 Вариант 2: ngrok — быстрый туннель для теста (5 минут)

**Плюсы:** не нужен деплой, работает прямо с твоего компьютера  
**Минусы:** адрес меняется при каждом запуске, работает только пока открыт терминал

### Шаг 1 — Установи ngrok

1. Скачай: https://ngrok.com/download
2. Зарегистрируйся: https://dashboard.ngrok.com/signup (бесплатно)
3. Скопируй токен: https://dashboard.ngrok.com/get-started/your-authtoken
4. Активируй токен:
```bash
ngrok config add-authtoken ВАШ_ТОКЕН_ЗДЕСЬ
```

### Шаг 2 — Запусти

**Windows (автоматически):**
```
cd server
ngrok-tunnel.bat
```

**Вручную (две команды в двух терминалах):**
```bash
# Терминал 1 — сервер
cd server
mvn package && java -jar target/chat-server.jar

# Терминал 2 — туннель
ngrok tcp 8888
```

### Шаг 3 — Скопируй адрес

В терминале ngrok ты увидишь:
```
Forwarding   tcp://0.tcp.eu.ngrok.io:15423 -> localhost:8888
```

Это и есть твой публичный адрес:
- **Хост:** `0.tcp.eu.ngrok.io`
- **Порт:** `15423` (каждый раз разный!)

Введи их в приложении на экране входа, или обнови `ServerConfig.kt`.

---

## 🥉 Вариант 3: Railway — деплой с GitHub (простой интерфейс)

**Плюсы:** красивый веб-интерфейс, привязка к GitHub  
**Минусы:** $5 бесплатного кредита в месяц (обычно хватает)

### Шаг 1 — Создай репозиторий GitHub

```bash
cd MessageOnline
git init
git add .
git commit -m "Initial commit"
# Создай репозиторий на github.com и следуй инструкциям
git push origin main
```

### Шаг 2 — Подключи Railway

1. Зайди на [railway.app](https://railway.app) → **New Project**
2. Выбери **Deploy from GitHub repo**
3. Выбери свой репозиторий
4. Railway автоматически найдёт `Dockerfile` в папке `server/`
5. Нажми **Add Root Directory** → укажи `server/`

### Шаг 3 — Включи TCP порт

В Railway Dashboard:
1. Кликни на своё приложение
2. **Settings** → **Networking** → **Add TCP Proxy**
3. Internal port: `8888`
4. Railway выдаст тебе публичный хост и порт, например:
   ```
   roundhouse.proxy.rlwy.net : 31234
   ```

### Шаг 4 — Настрой ServerConfig.kt

```kotlin
object ServerConfig {
    const val HOST = "roundhouse.proxy.rlwy.net"
    const val PORT = 31234   // ← порт из Railway
}
```

---

## 📱 Как подключиться с телефона после деплоя

После того как сервер в облаке, в приложении на экране входа:

| Поле | Значение |
|------|----------|
| IP-адрес сервера | `messageonline-ivan.fly.dev` (или адрес ngrok/Railway) |
| Порт | `8888` (или порт от Railway) |

> **Совет:** Обнови `ServerConfig.kt` — тогда адрес будет подставляться автоматически и не нужно вводить каждый раз.

---

## 🔄 Обновление сервера после изменений в коде

```bash
# Fly.io
cd server
flyctl deploy --app messageonline-ivan

# Railway — просто сделай git push, деплой запустится автоматически
git add . && git commit -m "Update" && git push
```

---

## ⚡ Сравнение вариантов

| | Fly.io | ngrok | Railway |
|---|:---:|:---:|:---:|
| Бесплатно | ✅ навсегда | ✅ | ✅ $5/мес |
| Постоянный адрес | ✅ | ❌ | ✅ |
| Постоянные данные | ✅ | ✅ (локально) | ✅ |
| TCP сокеты | ✅ | ✅ | ✅ |
| Сложность | Средняя | Легко | Легко |
| Для продакшена | ✅ | ❌ | ✅ |
| Рекомендуется | ⭐⭐⭐ | ⭐⭐ (тест) | ⭐⭐ |

---

## 🐛 Типичные проблемы

### "Connection refused" на телефоне
- Убедись что сервер запущен и деплой прошёл успешно
- Проверь что HOST в ServerConfig.kt совпадает с адресом на Fly.io
- Проверь логи: `flyctl logs --app messageonline-ivan`

### Данные теряются при перезапуске (Fly.io)
- Убедись что volume создан: `flyctl volumes list --app messageonline-ivan`
- В `fly.toml` должен быть раздел `[mounts]`

### ngrok: "Your account is limited to 1 simultaneous ngrok agent session"
- Бесплатный план ngrok = 1 туннель одновременно
- Закрой все другие ngrok процессы

### Railway: порт не открывается
- В Railway Dashboard → Settings → Networking → убедись что TCP Proxy включён
- Используй именно тот порт что Railway назначил (не 8888)

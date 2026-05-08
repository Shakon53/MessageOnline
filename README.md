# 💬 MessageOnline — Кроссплатформенный чат-мессенджер

> Учебный проект: клиент-серверный мессенджер наподобие WhatsApp с поддержкой Android и Desktop (Windows/Linux/macOS)

---

## 📐 Архитектура проекта

```
MessageOnline/
├── server/              ← Java-сервер (Maven, SQLite, TCP Sockets)
├── android-client/      ← Android приложение (Kotlin, MVVM, Material Design 3)
└── desktop-client/      ← Desktop приложение (Java, JavaFX)
```

### Схема взаимодействия

```
┌──────────────────────────────────────────────────────────────┐
│                      ChatServer (Java)                        │
│                                                               │
│  ServerSocket (порт 8888)                                     │
│       │                                                       │
│       ├─── ClientHandler [Thread 1] ◄──► Android Client 1    │
│       ├─── ClientHandler [Thread 2] ◄──► Android Client 2    │
│       ├─── ClientHandler [Thread 3] ◄──► Desktop Client      │
│       └─── ...                                                │
│                                                               │
│  DatabaseManager (SQLite)                                     │
│  ├── users (id, username, email, password_hash)               │
│  └── messages (sender, receiver, content, timestamp)          │
└──────────────────────────────────────────────────────────────┘
```

### Сетевой протокол

Все сообщения передаются как **JSON по TCP** (одна строка = один пакет):

| Тип пакета | Направление | Описание |
|---|---|---|
| `REGISTER` | Client→Server | Регистрация: username, email, password |
| `LOGIN` | Client→Server | Вход: username, password |
| `GLOBAL_MESSAGE` | Оба | Сообщение в общий чат |
| `PRIVATE_MESSAGE` | Оба | Личное сообщение |
| `GET_HISTORY` | Client→Server | Запрос истории |
| `USER_LIST` | Server→Client | Список онлайн пользователей |
| `USER_JOINED/LEFT` | Server→Client | Уведомление о подключении/отключении |

---

## 🖥️ Сервер (server/)

### Технологии
- **Java 17**
- **TCP Sockets** (ServerSocket, Socket)
- **SQLite** (через sqlite-jdbc)
- **org.json** (JSON парсинг)
- **Maven** (сборка)
- **Многопоточность** (ExecutorService, ConcurrentHashMap)

### Структура

```
server/src/main/java/server/
├── ChatServer.java           ← Точка входа, принимает подключения
├── ClientHandler.java        ← Обработчик одного клиента (Runnable)
├── database/
│   └── DatabaseManager.java  ← Singleton, работа с SQLite
├── model/
│   ├── Message.java          ← Модель сообщения
│   ├── Packet.java           ← Константы и фабрики JSON-пакетов
│   └── User.java             ← Модель пользователя
└── util/
    ├── PasswordUtil.java     ← SHA-256 хэширование паролей с солью
    └── ServerLogger.java     ← Логирование в консоль
```

### Как работает сервер

1. `ChatServer.start()` создаёт `ServerSocket` на порту 8888
2. При подключении клиента создаётся `ClientHandler` и запускается в пуле потоков
3. `ClientHandler` читает JSON-строки из сокета в цикле
4. Каждый пакет обрабатывается в `handlePacket()` — switch по типу
5. Сервер хранит авторизованных клиентов в `ConcurrentHashMap<username, ClientHandler>`
6. При рассылке перебирает всех клиентов из map

---

## 📱 Android клиент (android-client/)

### Технологии
- **Kotlin**
- **MVVM** (ViewModel + LiveData)
- **Material Design 3**
- **Kotlin Coroutines** (асинхронная работа с сокетом)
- **ViewBinding**
- **RecyclerView** с двумя типами элементов

### Структура

```
android-client/app/src/main/
├── AndroidManifest.xml
├── java/com/messageonline/android/
│   ├── adapter/
│   │   ├── MessageAdapter.kt   ← Адаптер сообщений (sent/received)
│   │   └── UsersAdapter.kt     ← Адаптер списка пользователей
│   ├── model/
│   │   ├── ChatMessage.kt      ← Data class сообщения
│   │   ├── OnlineUser.kt       ← Data class пользователя
│   │   └── Packet.kt           ← Константы типов пакетов
│   ├── network/
│   │   └── SocketManager.kt    ← Singleton: TCP соединение + Coroutines
│   ├── ui/
│   │   ├── LoginActivity.kt    ← Экран входа
│   │   ├── RegisterActivity.kt ← Экран регистрации
│   │   ├── MainActivity.kt     ← Главный экран (глобальный чат)
│   │   ├── PrivateChatActivity.kt ← Экран личного чата
│   │   └── UsersActivity.kt    ← Список онлайн пользователей
│   └── viewmodel/
│       └── ChatViewModel.kt    ← ViewModel: вся бизнес-логика
└── res/
    ├── layout/                 ← XML макеты экранов
    ├── values/                 ← Цвета, строки, темы
    └── values-night/           ← Тёмная тема
```

### Архитектурный паттерн (MVVM)

```
Activity/Fragment  ◄──observe──  ViewModel (LiveData)
       │                              │
       │──action──►                  │──►  SocketManager (IO Thread)
                                          │
                                          ▼
                                    TCP Socket ◄──► Server
```

---

## 🖥️ Desktop клиент (desktop-client/)

### Технологии
- **Java 17**
- **JavaFX 21** (GUI)
- **FXML** (декларативный UI)
- **CSS** (стилизация)
- **org.json** (JSON)
- **Maven**

### Структура

```
desktop-client/src/
├── main/java/com/messageonline/desktop/
│   ├── MainApp.java                    ← Точка входа JavaFX
│   ├── controller/
│   │   ├── LoginController.java        ← Контроллер входа/регистрации
│   │   └── ChatController.java         ← Контроллер главного чата
│   ├── model/
│   │   ├── ChatMessage.java
│   │   ├── OnlineUser.java
│   │   └── Packet.java
│   ├── network/
│   │   └── SocketManager.java          ← Singleton: TCP соединение
│   └── util/
│       └── DesktopLogger.java
└── main/resources/com/messageonline/desktop/
    ├── login.fxml                      ← UI входа (вкладки)
    ├── chat.fxml                       ← UI главного чата
    └── css/
        └── style.css                   ← Стили Material Design
```

---

## 🚀 Инструкция по запуску

### Требования

- **Java JDK 17+** — [скачать](https://adoptium.net/)
- **Maven 3.8+** — [скачать](https://maven.apache.org/download.cgi)
- **Android Studio Hedgehog+** — для Android клиента
- **Git** — для клонирования

### Шаг 1: Запуск сервера

```powershell
# Перейти в папку сервера
cd server

# Собрать JAR со всеми зависимостями
mvn package

# Запустить сервер (порт 8888 по умолчанию)
java -jar target/chat-server.jar

# Или с указанием порта
java -jar target/chat-server.jar 9000
```

**Ожидаемый вывод:**
```
[INFO] === MessageOnline Chat Server запущен ===
[INFO] Порт: 8888
[INFO] Максимум клиентов: 100
[INFO] Ожидание подключений...
```

### Шаг 2: Запуск Desktop клиента

```powershell
# Перейти в папку Desktop клиента
cd desktop-client

# Запустить через Maven плагин (JavaFX)
mvn javafx:run

# Или собрать и запустить JAR
mvn package
java -jar target/chat-desktop.jar
```

**Настройка подключения:**
- IP: `localhost` (если сервер на том же компьютере)
- Порт: `8888`
- Для другой сети: используйте IP адрес компьютера с сервером

### Шаг 3: Запуск Android клиента

1. Откройте `android-client/` в Android Studio
2. В `SocketManager.kt` измените `serverHost`:
   ```kotlin
   var serverHost: String = "192.168.X.X"  // IP вашего компьютера с сервером
   ```
3. Запустите на эмуляторе или реальном устройстве
4. При входе укажите IP сервера в поле "IP-адрес сервера"

> **Важно:** Android устройство и компьютер с сервером должны быть в одной Wi-Fi сети!

---

## 🔍 Тестирование

### Сценарий 1: Регистрация и вход

1. Запустите сервер
2. Откройте Desktop клиент
3. Вкладка "Регистрация" → введите данные → нажмите "Зарегистрироваться"
4. Вкладка "Вход" → введите логин/пароль → нажмите "Войти"
5. Откройте второй Desktop клиент (или Android)
6. Зарегистрируйте второго пользователя

### Сценарий 2: Глобальный чат

1. Оба клиента вошли в аккаунт
2. Введите сообщение в поле ввода → Enter или кнопка "Отправить"
3. Сообщение должно появиться у **обоих** клиентов

### Сценарий 3: Личный чат (Desktop)

1. В левой панели дважды кликните на имя пользователя
2. Введите личное сообщение
3. У второго пользователя появится уведомление

### Сценарий 4: Личный чат (Android)

1. Нажмите кнопку "Пользователи"
2. Выберите пользователя из списка
3. Откроется экран личного чата

### Сценарий 5: Отключение

1. Закройте один из клиентов
2. Остальные клиенты должны увидеть уведомление "X покинул чат"
3. Пользователь пропадает из списка онлайн

---

## 📦 База данных

Файл `chat.db` создаётся автоматически в папке запуска сервера.

```sql
-- Таблица пользователей
CREATE TABLE users (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    username     TEXT NOT NULL UNIQUE,
    email        TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,           -- SHA-256 с солью
    created_at   INTEGER NOT NULL
);

-- Таблица сообщений
CREATE TABLE messages (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id         INTEGER NOT NULL,
    sender_username   TEXT NOT NULL,
    receiver_id       INTEGER,             -- NULL для глобального чата
    receiver_username TEXT,
    content           TEXT NOT NULL,
    timestamp         INTEGER NOT NULL,    -- Unix timestamp (мс)
    is_global         INTEGER NOT NULL     -- 1 = глобальный, 0 = личный
);
```

---

## 🔐 Безопасность

- Пароли хранятся в хэшированном виде (SHA-256 + случайная соль)
- Один пользователь может быть подключён только с одного устройства
- Проверка валидации на стороне сервера

---

## 🌟 Функциональность

| Функция | Сервер | Android | Desktop |
|---|:---:|:---:|:---:|
| Регистрация | ✅ | ✅ | ✅ |
| Авторизация | ✅ | ✅ | ✅ |
| Глобальный чат | ✅ | ✅ | ✅ |
| Личные сообщения | ✅ | ✅ | ✅ |
| Список онлайн | ✅ | ✅ | ✅ |
| История сообщений | ✅ | ✅ | ✅ |
| Уведомления | ✅ | ✅ | ✅ |
| Тёмная тема | — | ✅ | — |
| Многопоточность | ✅ | ✅ | ✅ |

---

## 📊 UML-диаграмма классов сервера

```
┌─────────────────┐       ┌──────────────────┐
│   ChatServer    │1  *   │  ClientHandler   │
│─────────────────│───────│──────────────────│
│ port: int       │       │ socket: Socket   │
│ onlineClients   │       │ currentUser: User│
│ threadPool      │       │ reader           │
│─────────────────│       │ writer           │
│ start()         │       │──────────────────│
│ addClient()     │       │ run()            │
│ removeClient()  │       │ handlePacket()   │
│ broadcastAll()  │       │ handleLogin()    │
│ broadcastExcept()│      │ handleRegister() │
└─────────────────┘       │ send()           │
         │                │ cleanup()        │
         │                └──────────────────┘
         │                        │
         ▼                        ▼
┌─────────────────┐       ┌──────────────────┐
│ DatabaseManager │       │     Packet       │
│─────────────────│       │──────────────────│
│ connection      │       │ REGISTER         │
│─────────────────│       │ LOGIN            │
│ registerUser()  │       │ GLOBAL_MESSAGE   │
│ loginUser()     │       │ PRIVATE_MESSAGE  │
│ saveMessage()   │       │ USER_LIST        │
│ getGlobalHistory│       │ ...              │
│ getPrivateHist. │       └──────────────────┘
└─────────────────┘
```

---

## 🛠️ Расширение проекта

Идеи для улучшения:

1. **Группы/каналы** — создать таблицу `rooms` и поддержку групповых чатов
2. **Отправка файлов** — передача файлов через сокет или HTTP
3. **Push-уведомления** — Firebase Cloud Messaging для Android
4. **Шифрование** — TLS/SSL для шифрования трафика
5. **Статус "печатает..."** — новый тип пакета `TYPING`
6. **Прочитанность** — отметки о прочтении (✓✓)
7. **Эмодзи и форматирование** — Markdown поддержка
8. **Веб-клиент** — добавить WebSocket сервер для браузеров

---

## 👨‍💻 Об архитектуре (для курсовой работы)

### Паттерны проектирования

| Паттерн | Где используется | Описание |
|---|---|---|
| **Singleton** | DatabaseManager, SocketManager | Один экземпляр на приложение |
| **Observer** | LiveData, Callbacks | Уведомление UI об изменениях |
| **MVVM** | Android клиент | Разделение UI и бизнес-логики |
| **MVC** | Desktop клиент | FXML=View, Controller=Controller |
| **Thread Pool** | Сервер | ExecutorService для клиентов |
| **Factory Method** | Packet.java | Создание JSON пакетов |

### Ключевые технические решения

1. **JSON протокол** — каждое сообщение это JSON-объект в одну строку, что упрощает парсинг (readLine)
2. **ConcurrentHashMap** — потокобезопасное хранение онлайн-клиентов без явных блокировок
3. **Daemon thread** (Desktop) — поток чтения автоматически завершается с приложением
4. **Kotlin Coroutines** (Android) — асинхронная работа с сокетом без колбэк-ада
5. **ViewBinding** — безопасный доступ к View элементам без `findViewById()`

---

## 📝 Лицензия

Учебный проект. Свободно используйте и модифицируйте.

---

*MessageOnline v1.0 — Курсовой проект*

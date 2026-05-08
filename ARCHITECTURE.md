# 📐 Архитектура MessageOnline — Пояснительная записка

## 1. Общая архитектура системы

```
┌─────────────────────────────────────────────────────────────────────┐
│                         КЛИЕНТЫ                                      │
│                                                                       │
│  ┌──────────────────┐      ┌──────────────────┐                      │
│  │  Android Client  │      │  Desktop Client  │                      │
│  │  (Kotlin/MVVM)   │      │  (Java/JavaFX)   │                      │
│  └────────┬─────────┘      └────────┬─────────┘                      │
│           │  TCP Socket              │  TCP Socket                    │
└───────────┼──────────────────────────┼────────────────────────────────┘
            │                          │
            ▼                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         СЕРВЕР (Java)                                │
│                                                                       │
│   ServerSocket (порт 8888)                                           │
│         │                                                             │
│         ├── [Thread 1] ClientHandler ◄──► Android Client 1          │
│         ├── [Thread 2] ClientHandler ◄──► Android Client 2          │
│         ├── [Thread 3] ClientHandler ◄──► Desktop Client 1          │
│         └── [Thread N] ClientHandler ◄──► Desktop Client 2          │
│                                                                       │
│   ConcurrentHashMap<username, ClientHandler>  (онлайн-пользователи) │
│   ExecutorService (пул потоков, max 100)                             │
│                                                                       │
│   DatabaseManager (Singleton)                                         │
│         │                                                             │
│         └── SQLite: chat.db                                           │
│               ├── TABLE users                                         │
│               └── TABLE messages                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Сетевой протокол (JSON over TCP)

Каждое сообщение — одна строка JSON, завершённая символом `\n`.

### Пример диалога Клиент ↔ Сервер

```
CLIENT ──► SERVER:  {"type":"LOGIN","username":"alice","password":"pass"}
SERVER ──► CLIENT:  {"type":"LOGIN_SUCCESS","userId":1,"username":"alice"}
SERVER ──► CLIENT:  {"type":"USER_LIST","users":[{"id":1,"username":"alice","online":true}]}

CLIENT ──► SERVER:  {"type":"GLOBAL_MESSAGE","content":"Привет всем!"}
SERVER ──► ALL:     {"type":"GLOBAL_MESSAGE","senderId":1,"senderUsername":"alice","content":"Привет всем!","timestamp":1715000000000}

CLIENT ──► SERVER:  {"type":"PRIVATE_MESSAGE","receiverUsername":"bob","content":"Привет!"}
SERVER ──► BOB:     {"type":"PRIVATE_MESSAGE","senderId":1,"senderUsername":"alice","receiverId":2,"receiverUsername":"bob","content":"Привет!","timestamp":...}
SERVER ──► ALICE:   {"type":"PRIVATE_MESSAGE",...}  ← эхо отправителю
SERVER ──► BOB:     {"type":"NOTIFICATION","content":"Новое сообщение от alice"}
```

---

## 3. Многопоточность на сервере

```java
// ChatServer.java — принимает подключения
ExecutorService threadPool = Executors.newFixedThreadPool(100);

while (!serverSocket.isClosed()) {
    Socket client = serverSocket.accept();  // блокирует поток до подключения
    ClientHandler handler = new ClientHandler(client, server);
    threadPool.submit(handler);            // запускает handler в потоке из пула
}

// ClientHandler.java — каждый клиент в своём потоке
public void run() {
    while ((line = reader.readLine()) != null) {
        handlePacket(line);  // обрабатывает входящий JSON
    }
}
```

**Потокобезопасность:**
- `ConcurrentHashMap` — хранение клиентов без явных блокировок
- `synchronized void send()` — атомарная отправка сообщений
- `synchronized` методы в `DatabaseManager` — безопасный доступ к БД

---

## 4. Архитектура Android (MVVM)

```
┌─────────────────────────────────────────────┐
│              VIEW (Activity/XML)             │
│                                              │
│  LoginActivity    ──►  layout/activity_login │
│  MainActivity     ──►  layout/activity_main  │
│  PrivateChatActivity  ──► ...               │
│  UsersActivity    ──►  RecyclerView          │
└───────────────────┬─────────────────────────┘
                    │  observe LiveData
                    ▼
┌─────────────────────────────────────────────┐
│           VIEWMODEL (ChatViewModel)          │
│                                              │
│  globalMessages: LiveData<List<ChatMessage>> │
│  privateMessages: LiveData<...>              │
│  onlineUsers: LiveData<List<OnlineUser>>     │
│  connectionStatus: LiveData<Status>          │
│                                              │
│  fun sendGlobalMessage()                     │
│  fun sendPrivateMessage()                    │
│  fun loadGlobalHistory()                     │
│  fun connect() / login() / logout()          │
└───────────────────┬─────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│         MODEL / NETWORK (SocketManager)      │
│                                              │
│  object SocketManager (Singleton)            │
│  var serverHost / serverPort                 │
│                                              │
│  suspend fun connect()                       │
│  fun send(JSONObject)                        │
│  fun disconnect()                            │
│                                              │
│  onPacketReceived: (JSONObject) -> Unit      │
│  onConnected / onDisconnected callbacks      │
└───────────────────┬─────────────────────────┘
                    │  TCP Socket
                    ▼
               [ChatServer]
```

---

## 5. Архитектура Desktop (MVC с JavaFX)

```
┌──────────────────────────────────────┐
│         VIEW (FXML + CSS)            │
│  login.fxml  →  LoginController      │
│  chat.fxml   →  ChatController       │
│  style.css   →  Material Design UI   │
└─────────────────┬────────────────────┘
                  │  fx:controller
                  ▼
┌──────────────────────────────────────┐
│       CONTROLLER (Java)              │
│  LoginController.java                │
│    onLogin() / onRegister()          │
│                                      │
│  ChatController.java                 │
│    onSend() / onLogout()             │
│    handlePacket() → update ListView  │
└─────────────────┬────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────┐
│         MODEL / NETWORK              │
│  SocketManager.java (Singleton)      │
│  ChatMessage.java / OnlineUser.java  │
└──────────────────────────────────────┘
```

---

## 6. Паттерны проектирования

| Паттерн | Класс | Назначение |
|---------|-------|-----------|
| **Singleton** | `DatabaseManager`, `SocketManager` | Один экземпляр на процесс |
| **Observer** | `LiveData`, callback-функции | Реактивное обновление UI |
| **MVVM** | Android (`Activity` + `ViewModel`) | Разделение UI и логики |
| **MVC** | Desktop (`FXML` + `Controller`) | Разделение представления и логики |
| **Thread Pool** | `ExecutorService` на сервере | Управление потоками клиентов |
| **Factory Method** | `Packet.java` | Создание JSON-пакетов |
| **Command** | Обработчики `handleLogin()` и т.д. | Инкапсуляция команд |

---

## 7. Последовательность авторизации (Sequence Diagram)

```
Client          Server          Database
  │                │                │
  │──REGISTER──►   │                │
  │                │──registerUser─►│
  │                │◄───User────────│
  │◄─REG_SUCCESS──│                │
  │                │                │
  │──LOGIN──────►  │                │
  │                │──loginUser────►│
  │                │◄───User────────│
  │◄─LOGIN_SUCCESS─│                │
  │◄─USER_LIST─────│                │
  │                │──broadcast USER_JOINED──► Other clients
  │                │                │
  │──GLOBAL_MSG──► │                │
  │                │──saveMessage──►│
  │                │──broadcast ────► ALL clients
  │◄─GLOBAL_MSG────│                │
```

---

## 8. База данных

```sql
-- Схема БД (SQLite)

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,      -- имя пользователя (уникальное)
    email         TEXT NOT NULL UNIQUE,      -- email (уникальный)
    password_hash TEXT NOT NULL,             -- SHA-256 с солью
    created_at    INTEGER NOT NULL           -- Unix timestamp (мс)
);

CREATE TABLE messages (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id         INTEGER NOT NULL REFERENCES users(id),
    sender_username   TEXT NOT NULL,
    receiver_id       INTEGER,               -- NULL → глобальный чат
    receiver_username TEXT,                  -- NULL → глобальный чат
    content           TEXT NOT NULL,
    timestamp         INTEGER NOT NULL,      -- Unix timestamp (мс)
    is_global         INTEGER NOT NULL       -- 1 = глобальный, 0 = личный
);

-- Индекс для быстрой загрузки истории личных сообщений
CREATE INDEX idx_private_messages
ON messages(sender_id, receiver_id, is_global);
```

---

## 9. Безопасность паролей (SHA-256 + соль)

```java
// Хэширование (при регистрации)
byte[] salt = new SecureRandom().generateSeed(16);       // случайная соль
byte[] hash = SHA256(salt + password);                    // хэш
stored = base64(salt) + ":" + base64(hash);               // сохраняем в БД

// Проверка (при входе)
String[] parts = stored.split(":");
byte[] salt = base64decode(parts[0]);
byte[] expectedHash = base64decode(parts[1]);
byte[] actualHash = SHA256(salt + inputPassword);
return MessageDigest.isEqual(actualHash, expectedHash);   // защита от timing attack
```

---

## 10. Тёмная тема Android

Поддержка тёмной темы реализована через систему ресурсов Android:

```
res/
├── values/themes.xml        ← светлая тема (день)
└── values-night/themes.xml  ← тёмная тема (ночь)
```

Система Android автоматически выбирает нужный файл в зависимости от настроек устройства. Обе темы используют Material Design 3 цветовые токены, которые адаптируются к теме.

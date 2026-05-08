package server;

import org.json.JSONArray;
import org.json.JSONObject;
import server.database.DatabaseManager;
import server.model.Message;
import server.model.Packet;
import server.model.User;
import server.util.ServerLogger;

import java.io.*;
import java.net.Socket;

/**
 * Обработчик одного клиентского подключения.
 *
 * Каждый клиент получает свой поток (Thread).
 * Читает JSON-пакеты от клиента и отправляет ответы.
 *
 * Жизненный цикл:
 *   1. Клиент подключается -> создаётся ClientHandler
 *   2. Клиент логинится/регистрируется -> user != null
 *   3. Клиент обменивается сообщениями
 *   4. Клиент отключается -> cleanup()
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;

    // Текущий пользователь (null до авторизации)
    private User currentUser;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Настраиваем потоки ввода/вывода
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            ServerLogger.info("Новое подключение: " + socket.getInetAddress().getHostAddress());

            // Основной цикл чтения сообщений
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    handlePacket(line.trim());
                }
            }

        } catch (IOException e) {
            // Клиент отключился (нормальная ситуация)
            if (currentUser != null) {
                ServerLogger.info("Клиент отключился: " + currentUser.getUsername());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Разбирает входящий JSON-пакет и вызывает нужный обработчик.
     */
    private void handlePacket(String json) {
        try {
            JSONObject packet = new JSONObject(json);
            String type = packet.getString("type");

            switch (type) {
                case Packet.REGISTER        -> handleRegister(packet);
                case Packet.LOGIN           -> handleLogin(packet);
                case Packet.LOGOUT          -> cleanup();
                case Packet.GLOBAL_MESSAGE  -> handleGlobalMessage(packet);
                case Packet.PRIVATE_MESSAGE -> handlePrivateMessage(packet);
                case Packet.GET_HISTORY     -> handleGetHistory(packet);
                case Packet.GET_PRIVATE_HISTORY -> handleGetPrivateHistory(packet);
                case Packet.GET_USERS       -> handleGetUsers();
                default -> send(Packet.error("Неизвестный тип пакета: " + type));
            }

        } catch (Exception e) {
            ServerLogger.error("Ошибка обработки пакета: " + e.getMessage());
            send(Packet.error("Ошибка обработки запроса"));
        }
    }

    // ==================== ОБРАБОТЧИКИ ====================

    /** Регистрация нового пользователя */
    private void handleRegister(JSONObject p) {
        String username = p.optString("username", "").trim();
        String email    = p.optString("email", "").trim();
        String password = p.optString("password", "");

        // Валидация
        if (username.length() < 3) {
            send(Packet.registerFail("Имя пользователя слишком короткое (мин. 3 символа)"));
            return;
        }
        if (password.length() < 4) {
            send(Packet.registerFail("Пароль слишком короткий (мин. 4 символа)"));
            return;
        }
        if (!email.contains("@")) {
            send(Packet.registerFail("Неверный формат email"));
            return;
        }

        User user = DatabaseManager.getInstance().registerUser(username, email, password);
        if (user != null) {
            send(Packet.registerSuccess(user.getId(), user.getUsername()));
            ServerLogger.info("Регистрация: " + username);
        } else {
            send(Packet.registerFail("Имя пользователя или email уже заняты"));
        }
    }

    /** Авторизация пользователя */
    private void handleLogin(JSONObject p) {
        String username = p.optString("username", "").trim();
        String password = p.optString("password", "");

        // Уже авторизован?
        if (currentUser != null) {
            send(Packet.error("Вы уже авторизованы как " + currentUser.getUsername()));
            return;
        }

        // Проверяем, не онлайн ли уже этот пользователь
        if (server.isUserOnline(username)) {
            send(Packet.loginFail("Пользователь уже подключён с другого устройства"));
            return;
        }

        User user = DatabaseManager.getInstance().loginUser(username, password);
        if (user != null) {
            currentUser = user;
            currentUser.setOnline(true);

            // Регистрируемся на сервере как активный клиент
            server.addClient(this);

            // Отправляем успешный ответ
            send(Packet.loginSuccess(user.getId(), user.getUsername()));

            // Уведомляем всех остальных
            server.broadcastExcept(Packet.userJoined(user.getId(), user.getUsername()), this);

            // Отправляем список онлайн-пользователей
            send(buildUserListPacket());

            ServerLogger.info("Вход: " + username);
        } else {
            send(Packet.loginFail("Неверное имя пользователя или пароль"));
        }
    }

    /** Глобальное сообщение в общий чат */
    private void handleGlobalMessage(JSONObject p) {
        if (!checkAuthorized()) return;

        String content = p.optString("content", "").trim();
        if (content.isEmpty() || content.length() > 2000) {
            send(Packet.error("Сообщение пустое или слишком длинное"));
            return;
        }

        long timestamp = System.currentTimeMillis();
        Message msg = new Message(
                currentUser.getId(),
                currentUser.getUsername(),
                content,
                timestamp
        );

        // Сохраняем в БД
        DatabaseManager.getInstance().saveMessage(msg);

        // Рассылаем ВСЕМ подключённым клиентам (включая отправителя)
        String packet = Packet.globalMessage(msg);
        server.broadcastAll(packet);

        ServerLogger.chat("[GLOBAL] " + currentUser.getUsername() + ": " + content);
    }

    /** Личное сообщение конкретному пользователю */
    private void handlePrivateMessage(JSONObject p) {
        if (!checkAuthorized()) return;

        String receiverUsername = p.optString("receiverUsername", "").trim();
        String content = p.optString("content", "").trim();

        if (receiverUsername.isEmpty() || content.isEmpty()) {
            send(Packet.error("Укажите получателя и текст сообщения"));
            return;
        }
        if (receiverUsername.equals(currentUser.getUsername())) {
            send(Packet.error("Нельзя отправить сообщение самому себе"));
            return;
        }

        // Ищем получателя среди онлайн-клиентов
        ClientHandler receiver = server.getClientByUsername(receiverUsername);
        if (receiver == null) {
            send(Packet.error("Пользователь " + receiverUsername + " не в сети"));
            return;
        }

        long timestamp = System.currentTimeMillis();
        Message msg = new Message(
                currentUser.getId(),
                currentUser.getUsername(),
                receiver.currentUser.getId(),
                receiverUsername,
                content,
                timestamp
        );

        // Сохраняем в БД
        DatabaseManager.getInstance().saveMessage(msg);

        String packet = Packet.privateMessage(msg);

        // Отправляем получателю
        receiver.send(packet);
        // Эхо отправителю (чтобы увидел своё сообщение)
        send(packet);

        // Push-уведомление получателю
        receiver.send(Packet.notification(
                "Новое сообщение от " + currentUser.getUsername()));

        ServerLogger.chat("[PRIVATE] " + currentUser.getUsername()
                + " -> " + receiverUsername + ": " + content);
    }

    /** Запрос истории глобального чата */
    private void handleGetHistory(JSONObject p) {
        if (!checkAuthorized()) return;

        int limit = Math.min(p.optInt("limit", 50), 100);
        JSONArray messages = DatabaseManager.getInstance().getGlobalHistory(limit);

        String response = new JSONObject()
                .put("type", Packet.HISTORY_RESPONSE)
                .put("chatType", "global")
                .put("messages", messages)
                .toString();
        send(response);
    }

    /** Запрос истории личных сообщений */
    private void handleGetPrivateHistory(JSONObject p) {
        if (!checkAuthorized()) return;

        String otherUsername = p.optString("otherUsername", "").trim();
        int limit = Math.min(p.optInt("limit", 50), 100);

        User otherUser = DatabaseManager.getInstance().getUserByUsername(otherUsername);
        if (otherUser == null) {
            send(Packet.error("Пользователь не найден"));
            return;
        }

        JSONArray messages = DatabaseManager.getInstance()
                .getPrivateHistory(currentUser.getId(), otherUser.getId(), limit);

        String response = new JSONObject()
                .put("type", Packet.HISTORY_RESPONSE)
                .put("chatType", "private")
                .put("otherUsername", otherUsername)
                .put("messages", messages)
                .toString();
        send(response);
    }

    /** Запрос списка онлайн-пользователей */
    private void handleGetUsers() {
        if (!checkAuthorized()) return;
        send(buildUserListPacket());
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Формирует пакет со списком онлайн-пользователей.
     */
    private String buildUserListPacket() {
        JSONArray users = new JSONArray();
        for (ClientHandler client : server.getOnlineClients()) {
            if (client.currentUser != null) {
                users.put(new JSONObject()
                        .put("id", client.currentUser.getId())
                        .put("username", client.currentUser.getUsername())
                        .put("online", true));
            }
        }
        return new JSONObject()
                .put("type", Packet.USER_LIST)
                .put("users", users)
                .toString();
    }

    /**
     * Проверяет авторизацию.
     * @return true если пользователь авторизован
     */
    private boolean checkAuthorized() {
        if (currentUser == null) {
            send(Packet.error("Необходима авторизация"));
            return false;
        }
        return true;
    }

    /**
     * Отправляет JSON-строку клиенту.
     * Потокобезопасен — synchronized на writer.
     */
    public synchronized void send(String jsonLine) {
        if (writer != null && !socket.isClosed()) {
            writer.println(jsonLine);
        }
    }

    /**
     * Очищает ресурсы при отключении клиента.
     */
    private void cleanup() {
        if (currentUser != null) {
            // Уведомляем других о выходе
            server.broadcastExcept(
                    Packet.userLeft(currentUser.getId(), currentUser.getUsername()),
                    this);
            server.removeClient(this);
            ServerLogger.info("Отключён: " + currentUser.getUsername());
            currentUser = null;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            ServerLogger.error("Ошибка закрытия сокета: " + e.getMessage());
        }
    }

    /** Имя текущего пользователя (для поиска) */
    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }
}

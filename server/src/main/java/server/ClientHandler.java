package server;

import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;
import server.database.DatabaseManager;
import server.model.Message;
import server.model.Packet;
import server.model.User;
import server.util.ServerLogger;

public class ClientHandler {

    private final WebSocket conn;
    private final ChatServer server;
    private User currentUser;

    public ClientHandler(WebSocket conn, ChatServer server) {
        this.conn = conn;
        this.server = server;
    }

    // ==================== ОБРАБОТКА ПАКЕТОВ ====================

    public void handlePacket(String json) {
        try {
            JSONObject packet = new JSONObject(json);
            String type = packet.getString("type");

            switch (type) {
                case Packet.REGISTER            -> handleRegister(packet);
                case Packet.LOGIN               -> handleLogin(packet);
                case Packet.LOGOUT              -> cleanup();
                case Packet.GLOBAL_MESSAGE      -> handleGlobalMessage(packet);
                case Packet.PRIVATE_MESSAGE     -> handlePrivateMessage(packet);
                case Packet.GET_HISTORY         -> handleGetHistory(packet);
                case Packet.GET_PRIVATE_HISTORY -> handleGetPrivateHistory(packet);
                case Packet.GET_USERS           -> handleGetUsers();
                case Packet.TYPING              -> handleTyping(packet);
                case Packet.UPDATE_PROFILE      -> handleUpdateProfile(packet);
                default -> send(Packet.error("Неизвестный тип пакета: " + type));
            }

        } catch (Exception e) {
            ServerLogger.error("Ошибка обработки пакета: " + e.getMessage());
            send(Packet.error("Ошибка обработки запроса"));
        }
    }

    // ==================== ОБРАБОТЧИКИ ====================

    private void handleRegister(JSONObject p) {
        String username = p.optString("username", "").trim();
        String phone    = p.optString("phone", "").trim();
        String password = p.optString("password", "");

        if (username.length() < 3) {
            send(Packet.registerFail("Имя пользователя слишком короткое (мин. 3 символа)"));
            return;
        }
        if (password.length() < 4) {
            send(Packet.registerFail("Пароль слишком короткий (мин. 4 символа)"));
            return;
        }
        if (phone.length() < 5) {
            send(Packet.registerFail("Неверный email или номер"));
            return;
        }

        User user = DatabaseManager.getInstance().registerUser(username, phone, password);
        if (user != null) {
            send(Packet.registerSuccess(user.getId(), user.getUsername()));
            ServerLogger.info("Регистрация: " + username + " (" + phone + ")");
        } else {
            send(Packet.registerFail("Имя пользователя или email уже заняты"));
        }
    }

    private void handleLogin(JSONObject p) {
        String username = p.optString("username", "").trim();
        String password = p.optString("password", "");

        if (currentUser != null) {
            send(Packet.error("Вы уже авторизованы как " + currentUser.getUsername()));
            return;
        }
        if (server.isUserOnline(username)) {
            send(Packet.loginFail("Пользователь уже подключён с другого устройства"));
            return;
        }

        User user = DatabaseManager.getInstance().loginUser(username, password);
        if (user != null) {
            currentUser = user;
            currentUser.setOnline(true);
            server.addClient(this);

            send(Packet.loginSuccess(user.getId(), user.getUsername(),
                    user.getPhone(), user.getStatusText()));
            server.broadcastExcept(Packet.userJoined(user.getId(), user.getUsername()), this);
            send(buildUserListPacket());

            ServerLogger.info("Вход: " + username);
        } else {
            send(Packet.loginFail("Неверное имя пользователя или пароль"));
        }
    }

    private void handleGlobalMessage(JSONObject p) {
        if (!checkAuthorized()) return;

        String content = p.optString("content", "").trim();
        if (content.isEmpty() || content.length() > 2000) {
            send(Packet.error("Сообщение пустое или слишком длинное"));
            return;
        }

        Message msg = new Message(currentUser.getId(), currentUser.getUsername(),
                content, System.currentTimeMillis());
        DatabaseManager.getInstance().saveMessage(msg);
        server.broadcastAll(Packet.globalMessage(msg));
        ServerLogger.chat("[GLOBAL] " + currentUser.getUsername() + ": " + content);
    }

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

        ClientHandler receiver = server.getClientByUsername(receiverUsername);
        if (receiver == null) {
            send(Packet.error("Пользователь " + receiverUsername + " не в сети"));
            return;
        }

        Message msg = new Message(currentUser.getId(), currentUser.getUsername(),
                receiver.currentUser.getId(), receiverUsername, content, System.currentTimeMillis());
        DatabaseManager.getInstance().saveMessage(msg);

        String packet = Packet.privateMessage(msg);
        receiver.send(packet);
        send(packet);
        receiver.send(Packet.notification("Новое сообщение от " + currentUser.getUsername()));
        ServerLogger.chat("[PRIVATE] " + currentUser.getUsername() + " -> " + receiverUsername + ": " + content);
    }

    private void handleGetHistory(JSONObject p) {
        if (!checkAuthorized()) return;
        int limit = Math.min(p.optInt("limit", 50), 100);
        JSONArray messages = DatabaseManager.getInstance().getGlobalHistory(limit);
        send(new JSONObject()
                .put("type", Packet.HISTORY_RESPONSE)
                .put("chatType", "global")
                .put("messages", messages)
                .toString());
    }

    private void handleGetPrivateHistory(JSONObject p) {
        if (!checkAuthorized()) return;
        String otherUsername = p.optString("otherUsername", "").trim();
        int limit = Math.min(p.optInt("limit", 50), 100);

        User otherUser = DatabaseManager.getInstance().getUserByUsername(otherUsername);
        if (otherUser == null) { send(Packet.error("Пользователь не найден")); return; }

        JSONArray messages = DatabaseManager.getInstance()
                .getPrivateHistory(currentUser.getId(), otherUser.getId(), limit);
        send(new JSONObject()
                .put("type", Packet.HISTORY_RESPONSE)
                .put("chatType", "private")
                .put("otherUsername", otherUsername)
                .put("messages", messages)
                .toString());
    }

    private void handleGetUsers() {
        if (!checkAuthorized()) return;
        send(buildUserListPacket());
    }

    private void handleTyping(JSONObject p) {
        if (!checkAuthorized()) return;
        String receiverUsername = p.optString("receiverUsername", "").trim();
        boolean isTyping = p.optBoolean("isTyping", false);
        ClientHandler receiver = server.getClientByUsername(receiverUsername);
        if (receiver != null) {
            receiver.send(Packet.typing(currentUser.getUsername(), isTyping));
        }
    }

    private void handleUpdateProfile(JSONObject p) {
        if (!checkAuthorized()) return;
        String statusText = p.optString("statusText", "").trim();
        if (statusText.length() > 140) {
            send(Packet.error("Статус слишком длинный (макс. 140 символов)"));
            return;
        }
        boolean updated = DatabaseManager.getInstance().updateProfile(currentUser.getId(), statusText);
        if (updated) {
            currentUser.setStatusText(statusText);
            server.broadcastAll(Packet.profileUpdated(
                    currentUser.getId(), currentUser.getUsername(), statusText));
        } else {
            send(Packet.error("Не удалось сохранить профиль"));
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ====================

    private String buildUserListPacket() {
        JSONArray users = new JSONArray();
        for (ClientHandler client : server.getOnlineClients()) {
            if (client.currentUser != null) {
                users.put(new JSONObject()
                        .put("id", client.currentUser.getId())
                        .put("username", client.currentUser.getUsername())
                        .put("statusText", client.currentUser.getStatusText())
                        .put("online", true));
            }
        }
        return new JSONObject().put("type", Packet.USER_LIST).put("users", users).toString();
    }

    private boolean checkAuthorized() {
        if (currentUser == null) {
            send(Packet.error("Необходима авторизация"));
            return false;
        }
        return true;
    }

    public synchronized void send(String json) {
        try {
            if (conn.isOpen()) {
                conn.send(json);
            }
        } catch (Exception e) {
            ServerLogger.error("Ошибка отправки: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (currentUser != null) {
            server.broadcastExcept(
                    Packet.userLeft(currentUser.getId(), currentUser.getUsername()), this);
            server.removeClient(this);
            ServerLogger.info("Отключён: " + currentUser.getUsername());
            currentUser = null;
        }
    }

    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }
}

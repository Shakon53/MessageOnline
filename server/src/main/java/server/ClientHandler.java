package server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Notification;
import server.model.Message;
import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;
import server.database.DatabaseManager;
import server.model.Packet;
import server.model.User;
import server.util.ServerLogger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ClientHandler {

    private final WebSocket conn;
    private final ChatServer server;
    private User currentUser;

    private boolean isAdmin = false;

    public ClientHandler(WebSocket conn, ChatServer server) {
        this.conn = conn;
        this.server = server;
    }

    public int getUserId() {
        return currentUser != null ? currentUser.getId() : -1;
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
                case Packet.FCM_TOKEN           -> handleFCMToken(packet);
                case Packet.MARK_READ           -> handleMarkRead(packet);
                case Packet.EDIT_MESSAGE        -> handleEditMessage(packet);
                case Packet.UPDATE_AVATAR       -> handleUpdateAvatar(packet);
                case Packet.FRIEND_ADD    -> handleFriendAdd(packet);
                case Packet.FRIEND_ACCEPT -> handleFriendAccept(packet);
                case Packet.FRIEND_DECLINE-> handleFriendDecline(packet);
                case Packet.FRIEND_REMOVE -> handleFriendRemove(packet);
                case Packet.GET_FRIENDS   -> handleGetFriends();
                case Packet.DELETE_FOR_ALL  -> handleDeleteForAll(packet);
                case Packet.CHANGE_USERNAME -> handleChangeUsername(packet);
                case Packet.UPDATE_PRIVACY  -> handleUpdatePrivacy(packet);
                case Packet.ADMIN_LOGIN     -> handleAdminLogin(packet);
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
                    user.getPhone(), user.getStatusText(), user.getCreatedAt()));
            server.broadcastExcept(Packet.userJoined(user.getId(), user.getUsername()), this);
            server.notifyAdminsRaw("user_joined", new JSONObject()
                    .put("userId", user.getId())
                    .put("username", user.getUsername())
                    .put("onlineCount", server.getOnlineClients().size()));
            send(buildUserListPacket());
            handleGetFriends(); // Send friend list on login

            ServerLogger.info("Вход: " + username);
        } else {
            send(Packet.loginFail("Неверное имя пользователя или пароль"));
        }
    }

    private void handleGlobalMessage(JSONObject p) {
        if (!checkAuthorized()) return;

        String content = p.optString("content", "").trim();
        String messageType = p.optString("messageType", "text");
        // For images/audio allow larger content (base64)
        if (content.isEmpty() || (messageType.equals("text") && content.length() > 2000)) {
            send(Packet.error("Сообщение пустое или слишком длинное"));
            return;
        }

        Message msg = new Message(currentUser.getId(), currentUser.getUsername(),
                content, System.currentTimeMillis());
        msg.setMessageType(messageType);
        DatabaseManager.getInstance().saveMessage(msg);

        // Build packet, passthrough optional reply fields
        JSONObject out = new JSONObject(Packet.globalMessage(msg));
        String replyToSender  = p.optString("replyToSender", "");
        String replyToContent = p.optString("replyToContent", "");
        if (!replyToSender.isEmpty())  out.put("replyToSender",  replyToSender);
        if (!replyToContent.isEmpty()) out.put("replyToContent", replyToContent);

        server.broadcastAll(out.toString());
        ServerLogger.chat("[GLOBAL] " + currentUser.getUsername() + ": [" + messageType + "]");
    }

    private void handlePrivateMessage(JSONObject p) {
        if (!checkAuthorized()) return;

        String receiverUsername = p.optString("receiverUsername", "").trim();
        String content = p.optString("content", "").trim();
        String messageType = p.optString("messageType", "text");

        if (receiverUsername.isEmpty() || content.isEmpty()) {
            send(Packet.error("Укажите получателя и текст сообщения"));
            return;
        }
        if (receiverUsername.equals(currentUser.getUsername())) {
            send(Packet.error("Нельзя отправить сообщение самому себе"));
            return;
        }

        ClientHandler receiver = server.getClientByUsername(receiverUsername);

        // Получатель офлайн — пробуем отправить FCM push
        if (receiver == null) {
            User receiverUser = DatabaseManager.getInstance().getUserByUsername(receiverUsername);
            if (receiverUser == null) {
                send(Packet.error("Пользователь " + receiverUsername + " не найден"));
                return;
            }
            // Проверка приватности: только друзья
            if ("friends_only".equals(DatabaseManager.getInstance().getPrivacyMode(receiverUser.getId()))) {
                if (!DatabaseManager.getInstance().areFriends(currentUser.getId(), receiverUser.getId())) {
                    send(new org.json.JSONObject()
                            .put("type", Packet.PRIVACY_REJECTED)
                            .put("username", receiverUsername)
                            .toString());
                    return;
                }
            }
            // Сохраняем сообщение в историю
            Message msg = new Message(currentUser.getId(), currentUser.getUsername(),
                    receiverUser.getId(), receiverUsername, content, System.currentTimeMillis());
            msg.setMessageType(messageType);
            DatabaseManager.getInstance().saveMessage(msg);
            send(Packet.privateMessage(msg)); // эхо отправителю

            // Отправляем push уведомление (только для текстовых)
            String fcmToken = DatabaseManager.getInstance().getFCMToken(receiverUsername);
            if (fcmToken != null && !fcmToken.isEmpty()) {
                String pushBody = messageType.equals("text")
                        ? (content.length() > 100 ? content.substring(0, 100) + "..." : content)
                        : (messageType.equals("image") ? "📷 Фото" : "🎤 Голосовое сообщение");
                sendFCMPush(fcmToken, "💬 " + currentUser.getUsername(), pushBody, currentUser.getUsername());
            }
            ServerLogger.chat("[PRIVATE→offline] " + currentUser.getUsername() + " -> " + receiverUsername);
            return;
        }

        // Проверка приватности для онлайн-получателя
        if ("friends_only".equals(receiver.currentUser.getPrivacyMode())) {
            if (!DatabaseManager.getInstance().areFriends(currentUser.getId(), receiver.currentUser.getId())) {
                send(new org.json.JSONObject()
                        .put("type", Packet.PRIVACY_REJECTED)
                        .put("username", receiverUsername)
                        .toString());
                return;
            }
        }

        Message msg = new Message(currentUser.getId(), currentUser.getUsername(),
                receiver.currentUser.getId(), receiverUsername, content, System.currentTimeMillis());
        msg.setMessageType(messageType);
        DatabaseManager.getInstance().saveMessage(msg);

        // Build packet, passthrough optional reply fields
        JSONObject out = new JSONObject(Packet.privateMessage(msg));
        String replyToSender  = p.optString("replyToSender", "");
        String replyToContent = p.optString("replyToContent", "");
        if (!replyToSender.isEmpty())  out.put("replyToSender",  replyToSender);
        if (!replyToContent.isEmpty()) out.put("replyToContent", replyToContent);

        String packet = out.toString();
        receiver.send(packet);
        send(packet);
        receiver.send(Packet.notification("Новое сообщение от " + currentUser.getUsername()));
        ServerLogger.chat("[PRIVATE] " + currentUser.getUsername() + " -> " + receiverUsername + ": [" + messageType + "]");
    }

    private void handleDeleteForAll(JSONObject p) {
        if (!checkAuthorized()) return;
        long timestamp = p.optLong("timestamp", 0);
        boolean isGlobal = p.optBoolean("isGlobal", false);
        String receiverUsername = p.optString("receiverUsername", "");

        boolean deleted = DatabaseManager.getInstance().deleteMessage(currentUser.getUsername(), timestamp);
        if (!deleted) {
            send(Packet.error("Сообщение не найдено или нет прав на удаление"));
            return;
        }

        String packet = Packet.messageDeleted(currentUser.getUsername(), timestamp, isGlobal, receiverUsername);
        if (isGlobal) {
            server.broadcastAll(packet);
        } else {
            ClientHandler receiver = server.getClientByUsername(receiverUsername);
            if (receiver != null) receiver.send(packet);
            send(packet);
        }
        ServerLogger.info("DELETE_FOR_ALL: " + currentUser.getUsername() + " ts=" + timestamp);
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

    private void handleFCMToken(JSONObject p) {
        if (!checkAuthorized()) return;
        String token = p.optString("token", "").trim();
        if (!token.isEmpty()) {
            DatabaseManager.getInstance().updateFCMToken(currentUser.getId(), token);
            ServerLogger.info("FCM токен обновлён для: " + currentUser.getUsername());
        }
    }

    private void handleMarkRead(JSONObject p) {
        if (!checkAuthorized()) return;
        String peerUsername = p.optString("peerUsername", "").trim();
        ClientHandler peer = server.getClientByUsername(peerUsername);
        if (peer != null) {
            peer.send(new JSONObject()
                    .put("type", Packet.MESSAGE_READ)
                    .put("readerUsername", currentUser.getUsername())
                    .toString());
            ServerLogger.info("READ RECEIPT: " + currentUser.getUsername() + " прочитал чат с " + peerUsername);
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

    private void handleEditMessage(JSONObject p) {
        if (!checkAuthorized()) return;
        long timestamp = p.optLong("timestamp", 0);
        String newContent = p.optString("newContent", "").trim();
        boolean isGlobal = p.optBoolean("isGlobal", true);
        String receiverUsername = p.optString("receiverUsername", "");

        if (newContent.isEmpty() || newContent.length() > 2000) {
            send(Packet.error("Текст сообщения пустой или слишком длинный"));
            return;
        }

        boolean updated = DatabaseManager.getInstance().updateMessage(
                currentUser.getUsername(), timestamp, newContent);
        if (updated) {
            String packet = Packet.editedMessage(currentUser.getUsername(), timestamp, newContent, isGlobal, receiverUsername);
            if (isGlobal) {
                server.broadcastAll(packet);
            } else {
                ClientHandler receiver = server.getClientByUsername(receiverUsername);
                if (receiver != null) receiver.send(packet);
                send(packet);
            }
            ServerLogger.info("EDIT: " + currentUser.getUsername() + " ts=" + timestamp);
        } else {
            send(Packet.error("Не удалось найти сообщение для редактирования"));
        }
    }

    private void handleUpdateAvatar(JSONObject p) {
        if (!checkAuthorized()) return;
        String avatarUrl = p.optString("avatarUrl", "").trim();
        // Base64-encoded images can be 50–300 KB; allow up to ~400 KB worth of base64
        if (avatarUrl.length() > 524288) {
            send(Packet.error("Фото слишком большое (макс. ~400 КБ)"));
            return;
        }
        boolean updated = DatabaseManager.getInstance().updateAvatar(currentUser.getId(), avatarUrl);
        if (updated) {
            currentUser.setAvatarUrl(avatarUrl);
            // Broadcast updated user list so all clients see the new avatar
            server.broadcastAll(buildUserListPacket());
            ServerLogger.info("AVATAR UPDATE: " + currentUser.getUsername());
        }
    }

    private void handleFriendAdd(JSONObject p) {
        if (!checkAuthorized()) return;
        int targetId = p.optInt("targetUserId", -1);
        if (targetId <= 0 || targetId == currentUser.getId()) {
            send(Packet.error("Неверный ID пользователя")); return;
        }
        User target = DatabaseManager.getInstance().getUserById(targetId);
        if (target == null) {
            send(Packet.error("Пользователь с ID #" + targetId + " не найден")); return;
        }
        boolean ok = DatabaseManager.getInstance().sendFriendRequest(currentUser.getId(), targetId);
        if (!ok) {
            send(Packet.error("Запрос уже отправлен или вы уже друзья")); return;
        }
        send(Packet.notification("Запрос дружбы отправлен → " + target.getUsername()));
        // Notify target if online
        ClientHandler targetClient = server.getClientByUsername(target.getUsername());
        if (targetClient != null) {
            targetClient.send(Packet.friendRequestIn(
                    currentUser.getId(), currentUser.getUsername(),
                    currentUser.getStatusText(), currentUser.getAvatarUrl()));
        }
        ServerLogger.info("FRIEND_REQUEST: " + currentUser.getUsername() + " → " + target.getUsername());
    }

    private void handleFriendAccept(JSONObject p) {
        if (!checkAuthorized()) return;
        int fromId = p.optInt("fromUserId", -1);
        if (fromId <= 0) { send(Packet.error("Неверный ID")); return; }
        boolean ok = DatabaseManager.getInstance().acceptFriendRequest(fromId, currentUser.getId());
        if (!ok) { send(Packet.error("Запрос не найден")); return; }

        User requester = DatabaseManager.getInstance().getUserById(fromId);
        if (requester == null) { send(Packet.error("Пользователь не найден")); return; }

        // Tell me about the new friend
        send(Packet.friendAccepted(requester.getId(), requester.getUsername(),
                requester.getStatusText(), requester.getAvatarUrl()));
        // Tell the requester that their request was accepted
        ClientHandler requesterClient = server.getClientByUsername(requester.getUsername());
        if (requesterClient != null) {
            requesterClient.send(Packet.friendAccepted(
                    currentUser.getId(), currentUser.getUsername(),
                    currentUser.getStatusText(), currentUser.getAvatarUrl()));
        }
        ServerLogger.info("FRIEND_ACCEPTED: " + currentUser.getUsername() + " ← " + requester.getUsername());
    }

    private void handleFriendDecline(JSONObject p) {
        if (!checkAuthorized()) return;
        int fromId = p.optInt("fromUserId", -1);
        if (fromId <= 0) { send(Packet.error("Неверный ID")); return; }
        DatabaseManager.getInstance().removeFriend(fromId, currentUser.getId());
        send(Packet.notification("Запрос отклонён"));
    }

    private void handleFriendRemove(JSONObject p) {
        if (!checkAuthorized()) return;
        int friendId = p.optInt("friendUserId", -1);
        if (friendId <= 0) return;
        User friend = DatabaseManager.getInstance().getUserById(friendId);
        if (friend == null) return;
        DatabaseManager.getInstance().removeFriend(currentUser.getId(), friendId);
        send(Packet.friendRemoved(friendId, friend.getUsername()));
        // Also notify the other side if online
        ClientHandler friendClient = server.getClientByUsername(friend.getUsername());
        if (friendClient != null) {
            friendClient.send(Packet.friendRemoved(currentUser.getId(), currentUser.getUsername()));
        }
        ServerLogger.info("FRIEND_REMOVED: " + currentUser.getUsername() + " unfriended " + friend.getUsername());
    }

    private void handleGetFriends() {
        if (!checkAuthorized()) return;
        java.util.Set<String> onlineNames = new java.util.HashSet<>();
        for (ClientHandler c : server.getOnlineClients()) {
            if (c.currentUser != null) onlineNames.add(c.currentUser.getUsername());
        }
        send(DatabaseManager.getInstance().getFriendsList(currentUser.getId(), onlineNames).toString());
    }

    // ─── FCM v1 via Firebase Admin SDK ────────────────────────────────────────

    private static volatile boolean firebaseInitialized = false;

    private static synchronized void ensureFirebaseInitialized() {
        if (firebaseInitialized) return;
        try {
            String credJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            if (credJson == null || credJson.isEmpty()) {
                ServerLogger.info("FIREBASE_SERVICE_ACCOUNT_JSON не задан, FCM push отключён");
                return;
            }
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream credStream = new ByteArrayInputStream(
                        credJson.getBytes(StandardCharsets.UTF_8));
                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(credStream)
                        .createScoped("https://www.googleapis.com/auth/firebase.messaging");
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                FirebaseApp.initializeApp(options);
            }
            firebaseInitialized = true;
            ServerLogger.info("Firebase Admin SDK инициализирован");
        } catch (Exception e) {
            ServerLogger.error("Firebase init ошибка: " + e.getMessage());
        }
    }

    private void sendFCMPush(String token, String title, String body, String senderUsername) {
        ensureFirebaseInitialized();
        if (!firebaseInitialized) return;
        try {
            com.google.firebase.messaging.Message fcmMsg =
                    com.google.firebase.messaging.Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("title",        title)
                    .putData("body",         body)
                    .putData("chatType",     "private")
                    .putData("peerUsername", senderUsername)
                    .build();
            String response = FirebaseMessaging.getInstance().send(fcmMsg);
            ServerLogger.info("FCM v1 push отправлен: " + response);
        } catch (Exception e) {
            ServerLogger.error("FCM push ошибка: " + e.getMessage());
        }
    }

    private void handleChangeUsername(JSONObject p) {
        if (!checkAuthorized()) return;
        String newUsername = p.optString("newUsername", "").trim();
        if (newUsername.length() < 3 || newUsername.length() > 20) {
            send(new JSONObject().put("type", Packet.USERNAME_CHANGE_FAIL)
                    .put("message", "Имя должно быть от 3 до 20 символов").toString()); return;
        }
        if (!newUsername.matches("[a-zA-Z0-9_]+")) {
            send(new JSONObject().put("type", Packet.USERNAME_CHANGE_FAIL)
                    .put("message", "Только буквы, цифры и _").toString()); return;
        }
        if (server.isUserOnline(newUsername)) {
            send(new JSONObject().put("type", Packet.USERNAME_CHANGE_FAIL)
                    .put("message", "Имя уже занято").toString()); return;
        }
        String oldUsername = currentUser.getUsername();
        boolean ok = DatabaseManager.getInstance().changeUsername(currentUser.getId(), oldUsername, newUsername);
        if (ok) {
            server.removeClient(this);
            currentUser.setUsername(newUsername);
            server.addClient(this);
            send(new JSONObject().put("type", Packet.USERNAME_CHANGE_SUCCESS)
                    .put("newUsername", newUsername).toString());
            server.broadcastExcept(new JSONObject().put("type", Packet.USERNAME_CHANGED)
                    .put("oldUsername", oldUsername).put("newUsername", newUsername).toString(), this);
            ServerLogger.info("USERNAME CHANGED: " + oldUsername + " → " + newUsername);
        } else {
            send(new JSONObject().put("type", Packet.USERNAME_CHANGE_FAIL)
                    .put("message", "Имя уже занято").toString());
        }
    }

    private void handleUpdatePrivacy(JSONObject p) {
        if (!checkAuthorized()) return;
        String mode = p.optString("mode", "all");
        if (!mode.equals("all") && !mode.equals("friends_only")) {
            send(Packet.error("Неверный режим приватности")); return;
        }
        boolean ok = DatabaseManager.getInstance().updatePrivacy(currentUser.getId(), mode);
        if (ok) {
            currentUser.setPrivacyMode(mode);
            send(Packet.notification("Настройки приватности обновлены"));
            ServerLogger.info("PRIVACY: " + currentUser.getUsername() + " → " + mode);
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
                        .put("avatarUrl", client.currentUser.getAvatarUrl())
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
        if (isAdmin) {
            server.unregisterAdmin(this);
            isAdmin = false;
            return;
        }
        if (currentUser != null) {
            server.notifyAdminsRaw("user_left", new JSONObject()
                    .put("userId", currentUser.getId())
                    .put("username", currentUser.getUsername()));
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

    // ==================== ADMIN LOGIN ====================

    private void handleAdminLogin(JSONObject p) {
        String key = p.optString("key", "");
        if (!ChatServer.ADMIN_SECRET.equals(key)) {
            send(new JSONObject()
                    .put("type", Packet.ADMIN_LOGIN_FAIL)
                    .put("message", "Неверный ключ")
                    .toString());
            return;
        }
        isAdmin = true;
        server.registerAdmin(this);
        send(new JSONObject()
                .put("type", Packet.ADMIN_LOGIN_SUCCESS)
                .put("onlineCount", server.getOnlineClients().size())
                .toString());
        // Send DB stats to admin panel
        try {
            DatabaseManager dm = DatabaseManager.getInstance();
            JSONObject stats = dm.getAdminStats();
            JSONArray users  = dm.getAllUsers();
            JSONArray msgs   = dm.getRecentMessages(50);
            send(new JSONObject()
                    .put("type", Packet.ADMIN_STATS)
                    .put("userCount", stats.optInt("userCount", 0))
                    .put("messageCount", stats.optInt("messageCount", 0))
                    .put("users", users)
                    .put("recentMessages", msgs)
                    .toString());
        } catch (Exception e) {
            ServerLogger.error("[Admin] Ошибка отправки статистики: " + e.getMessage());
        }
        ServerLogger.info("[Admin] Admin panel авторизована");
    }
}

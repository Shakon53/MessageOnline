package server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import server.database.DatabaseManager;
import server.util.ServerLogger;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends WebSocketServer {

    private final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, ClientHandler> connHandlers = new ConcurrentHashMap<>();

    // Admin panel connections
    private final java.util.Set<ClientHandler> adminClients =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static final String ADMIN_SECRET =
            System.getenv("ADMIN_SECRET") != null ? System.getenv("ADMIN_SECRET") : "messageonline_admin_2025";

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        ClientHandler handler = new ClientHandler(conn, this);
        connHandlers.put(conn, handler);
        ServerLogger.info("Новое WS подключение от: "
                + conn.getRemoteSocketAddress().getAddress().getHostAddress()
                + " (всего клиентов: " + onlineClients.size() + ")");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientHandler handler = connHandlers.remove(conn);
        if (handler != null) {
            handler.cleanup();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ClientHandler handler = connHandlers.get(conn);
        if (handler != null && !message.isBlank()) {
            handler.handlePacket(message.trim());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ServerLogger.error("WebSocket ошибка: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        ServerLogger.info("=== MessageOnline WebSocket Server запущен на порту " + getPort() + " ===");
        ServerLogger.info("Ожидание подключений...");
    }

    // ==================== УПРАВЛЕНИЕ КЛИЕНТАМИ ====================

    public synchronized void addClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            onlineClients.put(handler.getUsername(), handler);
            ServerLogger.info("Онлайн: " + handler.getUsername()
                    + " (всего: " + onlineClients.size() + ")");
        }
    }

    public synchronized void removeClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            onlineClients.remove(handler.getUsername());
            ServerLogger.info("Офлайн: " + handler.getUsername()
                    + " (осталось: " + onlineClients.size() + ")");
        }
    }

    public boolean isUserOnline(String username) {
        return onlineClients.containsKey(username);
    }

    public ClientHandler getClientByUsername(String username) {
        return onlineClients.get(username);
    }

    public ClientHandler getClientById(int userId) {
        for (ClientHandler h : onlineClients.values()) {
            if (h.getUserId() == userId) return h;
        }
        return null;
    }

    public Collection<ClientHandler> getOnlineClients() {
        return onlineClients.values();
    }

    // ==================== РАССЫЛКА ====================

    public void broadcastAll(String message) {
        for (ClientHandler client : onlineClients.values()) {
            client.send(message);
        }
        notifyAdmins("broadcast", message);
    }

    public void broadcastExcept(String message, ClientHandler exclude) {
        for (ClientHandler client : onlineClients.values()) {
            if (client != exclude) {
                client.send(message);
            }
        }
        notifyAdmins("broadcast", message);
    }

    // ==================== ADMIN ====================

    public void registerAdmin(ClientHandler handler) {
        adminClients.add(handler);
        ServerLogger.info("[Admin] Подключился admin-клиент");
        // Send current online list to the new admin
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (ClientHandler c : onlineClients.values()) {
                arr.put(new org.json.JSONObject()
                        .put("username", c.getUsername())
                        .put("userId", c.getUserId()));
            }
            handler.send(new org.json.JSONObject()
                    .put("type", server.model.Packet.ADMIN_EVENT)
                    .put("event", "online_list")
                    .put("users", arr)
                    .put("count", onlineClients.size())
                    .toString());
        } catch (Exception ignored) {}
    }

    public void unregisterAdmin(ClientHandler handler) {
        adminClients.remove(handler);
    }

    public void notifyAdmins(String event, String rawJson) {
        if (adminClients.isEmpty()) return;
        try {
            org.json.JSONObject wrapper = new org.json.JSONObject()
                    .put("type", server.model.Packet.ADMIN_EVENT)
                    .put("event", event)
                    .put("payload", new org.json.JSONObject(rawJson))
                    .put("ts", System.currentTimeMillis());
            String msg = wrapper.toString();
            for (ClientHandler admin : adminClients) {
                admin.send(msg);
            }
        } catch (Exception ignored) {}
    }

    public void notifyAdminsRaw(String event, org.json.JSONObject data) {
        if (adminClients.isEmpty()) return;
        try {
            org.json.JSONObject wrapper = new org.json.JSONObject()
                    .put("type", server.model.Packet.ADMIN_EVENT)
                    .put("event", event)
                    .put("payload", data)
                    .put("ts", System.currentTimeMillis());
            String msg = wrapper.toString();
            for (ClientHandler admin : adminClients) {
                admin.send(msg);
            }
        } catch (Exception ignored) {}
    }

    // ==================== ТОЧКА ВХОДА ====================

    public static void main(String[] args) {
        // Railway задаёт PORT автоматически для HTTP-сервисов
        int port = 8080;

        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try { port = Integer.parseInt(envPort); } catch (NumberFormatException ignored) {}
        }

        String serverPort = System.getenv("SERVER_PORT");
        if (serverPort != null && !serverPort.isEmpty()) {
            try { port = Integer.parseInt(serverPort); } catch (NumberFormatException ignored) {}
        }

        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        DatabaseManager.getInstance();

        ChatServer server = new ChatServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServerLogger.info("Завершение работы сервера...");
            try {
                server.stop();
                DatabaseManager.getInstance().close();
            } catch (Exception e) {
                ServerLogger.error("Ошибка завершения: " + e.getMessage());
            }
        }));

        server.start();
    }
}

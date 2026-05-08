package com.messageonline.desktop.network;

import com.messageonline.desktop.model.Packet;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Менеджер сетевого соединения для Desktop клиента.
 *
 * Singleton — единственное соединение на всё приложение.
 * Читает пакеты в отдельном потоке (daemon thread).
 * Уведомляет контроллеры через Consumer<JSONObject> callback.
 */
public class SocketManager {

    private static SocketManager instance;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;

    private volatile boolean connected = false;

    // Callbacks
    private Consumer<JSONObject> packetListener;
    private Runnable disconnectListener;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    /**
     * Подключиться к серверу.
     * Вызывать из рабочего потока, НЕ из JavaFX Application Thread!
     */
    public boolean connect(String host, int port) {
        try {
            disconnect();

            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setKeepAlive(true);

            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            connected = true;
            startListening();
            return true;

        } catch (Exception e) {
            System.err.println("Ошибка подключения: " + e.getMessage());
            return false;
        }
    }

    /**
     * Запускает поток чтения входящих пакетов.
     * daemon=true — поток автоматически завершится при закрытии приложения.
     */
    private void startListening() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        try {
                            JSONObject json = new JSONObject(line);
                            if (packetListener != null) {
                                packetListener.accept(json);
                            }
                        } catch (Exception e) {
                            System.err.println("Ошибка парсинга: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Соединение разорвано: " + e.getMessage());
                }
            } finally {
                if (connected) {
                    connected = false;
                    if (disconnectListener != null) {
                        disconnectListener.run();
                    }
                }
            }
        }, "Socket-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Отключиться от сервера */
    public void disconnect() {
        connected = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка закрытия соединения: " + e.getMessage());
        }
        socket = null;
        reader = null;
        writer = null;
    }

    // ==================== ОТПРАВКА ====================

    /** Отправить JSON пакет серверу (потокобезопасен) */
    public synchronized void send(JSONObject json) {
        if (!connected || writer == null) return;
        writer.println(json.toString());
    }

    // ==================== ФАБРИЧНЫЕ МЕТОДЫ ====================

    public void sendLogin(String username, String password) {
        send(new JSONObject()
                .put("type", Packet.LOGIN)
                .put("username", username)
                .put("password", password));
    }

    public void sendRegister(String username, String email, String password) {
        send(new JSONObject()
                .put("type", Packet.REGISTER)
                .put("username", username)
                .put("email", email)
                .put("password", password));
    }

    public void sendGlobalMessage(String content) {
        send(new JSONObject()
                .put("type", Packet.GLOBAL_MESSAGE)
                .put("content", content));
    }

    public void sendPrivateMessage(String receiverUsername, String content) {
        send(new JSONObject()
                .put("type", Packet.PRIVATE_MESSAGE)
                .put("receiverUsername", receiverUsername)
                .put("content", content));
    }

    public void requestGlobalHistory() {
        send(new JSONObject()
                .put("type", Packet.GET_HISTORY)
                .put("limit", 50));
    }

    public void requestPrivateHistory(String otherUsername) {
        send(new JSONObject()
                .put("type", Packet.GET_PRIVATE_HISTORY)
                .put("otherUsername", otherUsername)
                .put("limit", 50));
    }

    public void requestUserList() {
        send(new JSONObject().put("type", Packet.GET_USERS));
    }

    public void sendLogout() {
        send(new JSONObject().put("type", Packet.LOGOUT));
    }

    // ==================== ГЕТТЕРЫ / СЕТТЕРЫ ====================

    public boolean isConnected() { return connected; }

    public void setPacketListener(Consumer<JSONObject> listener) {
        this.packetListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        this.disconnectListener = listener;
    }
}

package server;

import server.database.DatabaseManager;
import server.util.ServerLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ==========================================
 *   MessageOnline Chat Server
 *   Версия: 1.0
 * ==========================================
 *
 * Архитектура:
 *   - ServerSocket слушает порт PORT
 *   - Для каждого клиента создаётся ClientHandler в отдельном потоке
 *   - ExecutorService управляет пулом потоков (до 100 одновременных клиентов)
 *   - ConcurrentHashMap хранит авторизованных клиентов (username -> handler)
 *
 * Запуск: java -jar chat-server.jar [port]
 *         По умолчанию порт 8888
 */
public class ChatServer {

    /** Порт по умолчанию */
    public static final int DEFAULT_PORT = 8888;

    /** Максимальное число одновременных клиентов */
    private static final int MAX_CLIENTS = 100;

    private final int port;
    private ServerSocket serverSocket;

    /**
     * Потокобезопасная карта авторизованных клиентов.
     * Ключ: имя пользователя (username)
     * Значение: обработчик клиента (ClientHandler)
     */
    private final ConcurrentHashMap<String, ClientHandler> onlineClients =
            new ConcurrentHashMap<>();

    /** Пул потоков для обработки клиентов */
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(MAX_CLIENTS);

    public ChatServer(int port) {
        this.port = port;
    }

    /** Запускает сервер */
    public void start() {
        // Инициализируем БД
        DatabaseManager.getInstance();

        try {
            serverSocket = new ServerSocket(port);
            ServerLogger.info("=== MessageOnline Chat Server запущен ===");
            ServerLogger.info("Порт: " + port);
            ServerLogger.info("Максимум клиентов: " + MAX_CLIENTS);
            ServerLogger.info("Ожидание подключений...");
            ServerLogger.info("=========================================");

            // Хук для корректного завершения при Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                ServerLogger.info("Завершение работы сервера...");
                shutdown();
            }));

            // Основной цикл принятия подключений
            acceptClients();

        } catch (IOException e) {
            ServerLogger.error("Ошибка запуска сервера: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Цикл принятия клиентских подключений.
     * Блокирует поток до получения нового соединения.
     */
    private void acceptClients() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Настраиваем таймаут keepalive
                clientSocket.setKeepAlive(true);
                clientSocket.setSoTimeout(0); // Без таймаута чтения

                // Создаём обработчик и запускаем в пуле потоков
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.submit(handler);

                ServerLogger.info("Новое TCP подключение от: "
                        + clientSocket.getInetAddress().getHostAddress()
                        + " (всего клиентов: " + onlineClients.size() + ")");

            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    ServerLogger.error("Ошибка принятия подключения: " + e.getMessage());
                }
            }
        }
    }

    // ==================== УПРАВЛЕНИЕ КЛИЕНТАМИ ====================

    /** Добавить авторизованного клиента */
    public synchronized void addClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            onlineClients.put(handler.getUsername(), handler);
            ServerLogger.info("Онлайн: " + handler.getUsername()
                    + " (всего: " + onlineClients.size() + ")");
        }
    }

    /** Удалить клиента (при отключении) */
    public synchronized void removeClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            onlineClients.remove(handler.getUsername());
            ServerLogger.info("Офлайн: " + handler.getUsername()
                    + " (осталось: " + onlineClients.size() + ")");
        }
    }

    /** Проверить, онлайн ли пользователь */
    public boolean isUserOnline(String username) {
        return onlineClients.containsKey(username);
    }

    /** Получить обработчик клиента по имени */
    public ClientHandler getClientByUsername(String username) {
        return onlineClients.get(username);
    }

    /** Получить всех онлайн клиентов */
    public Collection<ClientHandler> getOnlineClients() {
        return onlineClients.values();
    }

    // ==================== РАССЫЛКА ====================

    /**
     * Отправить сообщение ВСЕМ онлайн-клиентам.
     */
    public void broadcastAll(String message) {
        for (ClientHandler client : onlineClients.values()) {
            client.send(message);
        }
    }

    /**
     * Отправить сообщение всем КРОМЕ указанного клиента.
     */
    public void broadcastExcept(String message, ClientHandler exclude) {
        for (ClientHandler client : onlineClients.values()) {
            if (client != exclude) {
                client.send(message);
            }
        }
    }

    // ==================== ЗАВЕРШЕНИЕ ====================

    /** Корректное завершение работы сервера */
    private void shutdown() {
        try {
            threadPool.shutdownNow();
            DatabaseManager.getInstance().close();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            ServerLogger.error("Ошибка завершения: " + e.getMessage());
        }
    }

    // ==================== ТОЧКА ВХОДА ====================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        // 1. Читаем порт из переменной окружения SERVER_PORT (для Docker/облака)
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        // 2. Аргумент командной строки имеет приоритет над env
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("Порт должен быть от 1 до 65535");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат порта: " + args[0]);
                System.exit(1);
            }
        }

        new ChatServer(port).start();
    }
}

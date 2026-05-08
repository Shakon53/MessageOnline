package server.database;

import org.json.JSONArray;
import org.json.JSONObject;
import server.model.Message;
import server.model.User;
import server.util.PasswordUtil;
import server.util.ServerLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер базы данных SQLite.
 * Singleton - единственный экземпляр на всё приложение.
 *
 * Таблицы:
 *   users    - зарегистрированные пользователи
 *   messages - история сообщений
 */
public class DatabaseManager {

    // Путь к базе данных:
    //   - локально:  chat.db (рядом с JAR)
    //   - в Docker:  /data/chat.db (из переменной окружения DB_PATH → persistent volume)
    private static final String DB_FILE =
            System.getenv("DB_PATH") != null ? System.getenv("DB_PATH") : "chat.db";
    private static DatabaseManager instance;
    private Connection connection;

    /** Приватный конструктор (Singleton) */
    private DatabaseManager() {
        connect();
        createTables();
    }

    /** Получить единственный экземпляр (thread-safe) */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /** Подключиться к SQLite */
    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            // Включаем внешние ключи
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            ServerLogger.info("База данных подключена: " + DB_FILE);
        } catch (Exception e) {
            ServerLogger.error("Ошибка подключения к БД: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /** Создать таблицы при первом запуске */
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {

            // Таблица пользователей
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    username     TEXT    NOT NULL UNIQUE,
                    email        TEXT    NOT NULL UNIQUE,
                    password_hash TEXT   NOT NULL,
                    created_at   INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                )
            """);

            // Таблица сообщений
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender_id        INTEGER NOT NULL,
                    sender_username  TEXT    NOT NULL,
                    receiver_id      INTEGER,
                    receiver_username TEXT,
                    content          TEXT    NOT NULL,
                    timestamp        INTEGER NOT NULL,
                    is_global        INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (sender_id) REFERENCES users(id)
                )
            """);

            // Индекс для ускорения поиска личных сообщений
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_private_messages
                ON messages(sender_id, receiver_id, is_global)
            """);

            ServerLogger.info("Таблицы БД готовы");
        } catch (SQLException e) {
            ServerLogger.error("Ошибка создания таблиц: " + e.getMessage());
        }
    }

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    /**
     * Регистрация нового пользователя.
     * @return User с id если успешно, null если имя/email уже занято
     */
    public synchronized User registerUser(String username, String email, String password) {
        // Проверяем уникальность
        if (isUsernameTaken(username)) return null;

        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, PasswordUtil.hashPassword(password));
            ps.executeUpdate();

            // Получаем сгенерированный id
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                ServerLogger.info("Зарегистрирован пользователь: " + username + " (id=" + id + ")");
                return new User(id, username, email);
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка регистрации: " + e.getMessage());
        }
        return null;
    }

    /**
     * Авторизация пользователя.
     * @return User если данные верны, null если нет
     */
    public synchronized User loginUser(String username, String password) {
        String sql = "SELECT id, username, email, password_hash FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (PasswordUtil.verifyPassword(password, storedHash)) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            storedHash
                    );
                }
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка входа: " + e.getMessage());
        }
        return null; // Неверный логин/пароль
    }

    /** Проверить занятость имени пользователя */
    private boolean isUsernameTaken(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Найти пользователя по имени.
     * Используется для загрузки истории личных сообщений, даже если собеседник офлайн.
     */
    public synchronized User getUserByUsername(String username) {
        String sql = "SELECT id, username, email, password_hash FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                );
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка поиска пользователя: " + e.getMessage());
        }
        return null;
    }

    // ==================== СООБЩЕНИЯ ====================

    /**
     * Сохранить сообщение в историю.
     */
    public synchronized void saveMessage(Message msg) {
        String sql = """
            INSERT INTO messages
                (sender_id, sender_username, receiver_id, receiver_username,
                 content, timestamp, is_global)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, msg.getSenderId());
            ps.setString(2, msg.getSenderUsername());
            if (msg.getReceiverId() != null) {
                ps.setInt(3, msg.getReceiverId());
                ps.setString(4, msg.getReceiverUsername());
            } else {
                ps.setNull(3, Types.INTEGER);
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, msg.getContent());
            ps.setLong(6, msg.getTimestamp());
            ps.setInt(7, msg.isGlobal() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            ServerLogger.error("Ошибка сохранения сообщения: " + e.getMessage());
        }
    }

    /**
     * Получить историю глобального чата.
     * @param limit количество последних сообщений
     * @return JSON массив сообщений
     */
    public synchronized JSONArray getGlobalHistory(int limit) {
        String sql = """
            SELECT sender_id, sender_username, content, timestamp
            FROM messages
            WHERE is_global = 1
            ORDER BY timestamp DESC
            LIMIT ?
        """;
        return fetchMessages(sql, limit, true);
    }

    /**
     * Получить историю личных сообщений между двумя пользователями.
     */
    public synchronized JSONArray getPrivateHistory(int userId1, int userId2, int limit) {
        String sql = """
            SELECT sender_id, sender_username, receiver_id, receiver_username,
                   content, timestamp
            FROM messages
            WHERE is_global = 0
              AND ((sender_id = ? AND receiver_id = ?)
                OR (sender_id = ? AND receiver_id = ?))
            ORDER BY timestamp DESC
            LIMIT ?
        """;
        JSONArray result = new JSONArray();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ps.setInt(3, userId2);
            ps.setInt(4, userId1);
            ps.setInt(5, limit);

            ResultSet rs = ps.executeQuery();
            List<JSONObject> msgs = new ArrayList<>();
            while (rs.next()) {
                msgs.add(new JSONObject()
                        .put("senderId",          rs.getInt("sender_id"))
                        .put("senderUsername",    rs.getString("sender_username"))
                        .put("receiverId",        rs.getInt("receiver_id"))
                        .put("receiverUsername",  rs.getString("receiver_username"))
                        .put("content",           rs.getString("content"))
                        .put("timestamp",         rs.getLong("timestamp"))
                        .put("isGlobal",          false));
            }
            // Возвращаем в хронологическом порядке (старые первыми)
            for (int i = msgs.size() - 1; i >= 0; i--) {
                result.put(msgs.get(i));
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка загрузки личных сообщений: " + e.getMessage());
        }
        return result;
    }

    /** Вспомогательный метод получения сообщений */
    private JSONArray fetchMessages(String sql, int limit, boolean isGlobal) {
        JSONArray result = new JSONArray();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            List<JSONObject> msgs = new ArrayList<>();
            while (rs.next()) {
                JSONObject obj = new JSONObject()
                        .put("senderId",       rs.getInt("sender_id"))
                        .put("senderUsername", rs.getString("sender_username"))
                        .put("content",        rs.getString("content"))
                        .put("timestamp",      rs.getLong("timestamp"))
                        .put("isGlobal",       isGlobal);
                msgs.add(obj);
            }
            // Возвращаем в хронологическом порядке
            for (int i = msgs.size() - 1; i >= 0; i--) {
                result.put(msgs.get(i));
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка получения истории: " + e.getMessage());
        }
        return result;
    }

    /** Закрыть соединение с БД */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                ServerLogger.info("База данных закрыта");
            }
        } catch (SQLException e) {
            ServerLogger.error("Ошибка закрытия БД: " + e.getMessage());
        }
    }
}

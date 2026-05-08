package server.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Логгер сервера.
 * Выводит сообщения в консоль и записывает в файл server.log.
 */
public class ServerLogger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter fileWriter;

    static {
        try {
            // Открываем файл логов (append=true - не затираем старые логи)
            fileWriter = new PrintWriter(new FileWriter("server.log", true), true);
        } catch (IOException e) {
            System.err.println("Не удалось открыть файл логов: " + e.getMessage());
        }
    }

    /** Информационное сообщение */
    public static void info(String message) {
        log("INFO ", message);
    }

    /** Предупреждение */
    public static void warn(String message) {
        log("WARN ", message);
    }

    /** Ошибка */
    public static void error(String message) {
        log("ERROR", message);
    }

    /** Сообщение о чате */
    public static void chat(String message) {
        log("CHAT ", message);
    }

    private static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = "[" + timestamp + "] [" + level + "] " + message;

        // Выводим в консоль с цветами ANSI
        String colored = colorize(level, line);
        System.out.println(colored);

        // Записываем в файл (без цветовых кодов)
        if (fileWriter != null) {
            fileWriter.println(line);
        }
    }

    private static String colorize(String level, String line) {
        return switch (level.trim()) {
            case "INFO"  -> "[32m" + line + "[0m";  // Зелёный
            case "WARN"  -> "[33m" + line + "[0m";  // Жёлтый
            case "ERROR" -> "[31m" + line + "[0m";  // Красный
            case "CHAT"  -> "[36m" + line + "[0m";  // Голубой
            default      -> line;
        };
    }
}

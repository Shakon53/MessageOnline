package com.messageonline.desktop.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Простой логгер для Desktop клиента.
 * Выводит сообщения в консоль с временной меткой.
 */
public class DesktopLogger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Информационное сообщение */
    public static void info(String message) {
        log("INFO", message);
    }

    /** Ошибка */
    public static void error(String message) {
        log("ERROR", message);
    }

    /** Отладочное сообщение */
    public static void debug(String message) {
        log("DEBUG", message);
    }

    private static void log(String level, String message) {
        String time = LocalDateTime.now().format(FORMATTER);
        System.out.printf("[%s] [%s] %s%n", time, level, message);
    }
}

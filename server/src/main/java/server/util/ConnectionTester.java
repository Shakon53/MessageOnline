package server.util;

import org.json.JSONObject;

import java.io.*;
import java.net.Socket;

/**
 * Утилита для тестирования соединения с сервером.
 *
 * Запуск:
 *   java -cp target/chat-server.jar server.util.ConnectionTester [host] [port]
 *
 * Тестирует: подключение, регистрацию, вход, отправку сообщения, выход.
 */
public class ConnectionTester {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : 8888;

        System.out.println("=== Тест подключения к серверу ===");
        System.out.println("Адрес: " + host + ":" + port);
        System.out.println();

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)) {

            System.out.println("✓ Подключение установлено");

            // Тест 1: Регистрация
            long uid = System.currentTimeMillis();
            String testUser = "test_" + uid;
            String testPass = "pass1234";
            String testEmail = testUser + "@test.com";

            send(writer, new JSONObject()
                    .put("type", "REGISTER")
                    .put("username", testUser)
                    .put("email", testEmail)
                    .put("password", testPass));

            String response = reader.readLine();
            JSONObject resp = new JSONObject(response);
            if ("REGISTER_SUCCESS".equals(resp.optString("type"))) {
                System.out.println("✓ Регистрация: " + testUser);
            } else {
                System.out.println("✗ Регистрация не удалась: " + resp.optString("message"));
                return;
            }

            // Тест 2: Вход
            send(writer, new JSONObject()
                    .put("type", "LOGIN")
                    .put("username", testUser)
                    .put("password", testPass));

            response = reader.readLine();
            resp = new JSONObject(response);
            if ("LOGIN_SUCCESS".equals(resp.optString("type"))) {
                System.out.println("✓ Вход: userId=" + resp.optInt("userId"));
            } else {
                System.out.println("✗ Вход не удался: " + resp.optString("message"));
                return;
            }

            // Пропускаем USER_LIST пакет
            reader.readLine();

            // Тест 3: Отправка сообщения
            send(writer, new JSONObject()
                    .put("type", "GLOBAL_MESSAGE")
                    .put("content", "Тестовое сообщение от ConnectionTester"));

            response = reader.readLine();
            resp = new JSONObject(response);
            if ("GLOBAL_MESSAGE".equals(resp.optString("type"))) {
                System.out.println("✓ Отправка сообщения: OK");
            } else {
                System.out.println("✗ Ошибка отправки: " + response);
                return;
            }

            // Тест 4: Выход
            send(writer, new JSONObject().put("type", "LOGOUT"));
            System.out.println("✓ Выход: OK");

            System.out.println();
            System.out.println("=== Все тесты пройдены успешно ✓ ===");

        } catch (Exception e) {
            System.out.println("✗ Ошибка: " + e.getMessage());
            System.out.println("Убедитесь, что сервер запущен на " + host + ":" + port);
        }
    }

    private static void send(PrintWriter writer, JSONObject json) {
        writer.println(json.toString());
    }
}

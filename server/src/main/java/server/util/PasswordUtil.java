package server.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Утилита для хэширования паролей.
 * Использует SHA-256 с случайной солью для безопасного хранения паролей.
 *
 * Формат хранения: base64(соль):base64(хэш)
 */
public class PasswordUtil {

    /**
     * Хэширует пароль с случайной солью.
     * @param password открытый пароль
     * @return строка в формате "соль:хэш"
     */
    public static String hashPassword(String password) {
        try {
            // Генерируем случайную соль (16 байт)
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            // Хэшируем пароль с солью
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes("UTF-8"));

            // Возвращаем "соль:хэш" в base64
            return Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка хэширования пароля", e);
        }
    }

    /**
     * Проверяет пароль против сохранённого хэша.
     * @param password проверяемый пароль
     * @param storedHash сохранённый хэш в формате "соль:хэш"
     * @return true если пароль совпадает
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) return false;

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            // Хэшируем введённый пароль с той же солью
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] actualHash = md.digest(password.getBytes("UTF-8"));

            // Сравниваем хэши (защита от timing attacks)
            return MessageDigest.isEqual(actualHash, expectedHash);

        } catch (Exception e) {
            return false;
        }
    }
}

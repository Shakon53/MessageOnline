package server.model;

/**
 * Модель пользователя.
 * Хранит данные зарегистрированного пользователя.
 */
public class User {
    private int id;
    private String username;
    private String phone;
    private String passwordHash;
    private String statusText = "Привет, я использую MessageOnline";
    private boolean online;
    private String avatarUrl = "";
    private String privacyMode = "all";
    private long createdAt = 0;

    public User() {}

    public User(int id, String username, String phone) {
        this.id = id;
        this.username = username;
        this.phone = phone;
    }

    public User(int id, String username, String phone, String passwordHash) {
        this.id = id;
        this.username = username;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getPrivacyMode() { return privacyMode; }
    public void setPrivacyMode(String privacyMode) { this.privacyMode = privacyMode; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', online=" + online + "}";
    }
}

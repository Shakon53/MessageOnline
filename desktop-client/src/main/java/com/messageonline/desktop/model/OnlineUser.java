package com.messageonline.desktop.model;

/**
 * Онлайн-пользователь.
 */
public class OnlineUser {
    private final int id;
    private final String username;
    private final boolean online;

    public OnlineUser(int id, String username, boolean online) {
        this.id = id;
        this.username = username;
        this.online = online;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }

    @Override
    public String toString() { return username; }
}

package com.messageonline.desktop.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Модель сообщения чата для Desktop клиента.
 */
public class ChatMessage {
    private final int senderId;
    private final String senderUsername;
    private final Integer receiverId;
    private final String receiverUsername;
    private final String content;
    private final long timestamp;
    private final boolean isGlobal;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm");

    public ChatMessage(int senderId, String senderUsername,
                       String content, long timestamp, boolean isGlobal) {
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.receiverId = null;
        this.receiverUsername = null;
        this.content = content;
        this.timestamp = timestamp;
        this.isGlobal = isGlobal;
    }

    public ChatMessage(int senderId, String senderUsername,
                       int receiverId, String receiverUsername,
                       String content, long timestamp) {
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.receiverId = receiverId;
        this.receiverUsername = receiverUsername;
        this.content = content;
        this.timestamp = timestamp;
        this.isGlobal = false;
    }

    public String getFormattedTime() {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        long diffMs = System.currentTimeMillis() - timestamp;
        return diffMs < 86_400_000L ? dt.format(TIME_FMT) : dt.format(DATETIME_FMT);
    }

    public boolean isMine(String myUsername) {
        return senderUsername.equals(myUsername);
    }

    // Геттеры
    public int getSenderId() { return senderId; }
    public String getSenderUsername() { return senderUsername; }
    public Integer getReceiverId() { return receiverId; }
    public String getReceiverUsername() { return receiverUsername; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isGlobal() { return isGlobal; }
}

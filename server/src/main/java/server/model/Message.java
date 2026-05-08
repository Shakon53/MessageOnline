package server.model;

/**
 * Модель сообщения.
 * Хранит данные одного сообщения в чате.
 */
public class Message {
    private int id;
    private int senderId;
    private String senderUsername;
    private Integer receiverId;       // null для глобального чата
    private String receiverUsername;  // null для глобального чата
    private String content;
    private long timestamp;           // Unix timestamp в миллисекундах
    private boolean isGlobal;         // true = глобальный чат, false = личное сообщение

    public Message() {}

    // Конструктор для глобального сообщения
    public Message(int senderId, String senderUsername, String content, long timestamp) {
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.timestamp = timestamp;
        this.isGlobal = true;
    }

    // Конструктор для личного сообщения
    public Message(int senderId, String senderUsername,
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

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public Integer getReceiverId() { return receiverId; }
    public void setReceiverId(Integer receiverId) { this.receiverId = receiverId; }

    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isGlobal() { return isGlobal; }
    public void setGlobal(boolean global) { isGlobal = global; }

    @Override
    public String toString() {
        return "Message{from=" + senderUsername + ", content='" + content + "', global=" + isGlobal + "}";
    }
}

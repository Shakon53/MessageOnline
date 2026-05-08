package com.messageonline.desktop.controller;

import com.messageonline.desktop.MainApp;
import com.messageonline.desktop.model.ChatMessage;
import com.messageonline.desktop.model.OnlineUser;
import com.messageonline.desktop.model.Packet;
import com.messageonline.desktop.network.SocketManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер главного окна чата.
 *
 * Левая панель: список онлайн-пользователей
 * Центральная: сообщения глобального чата
 * Нижняя: поле ввода + кнопка отправки
 */
public class ChatController {

    // ==================== FXML ====================
    @FXML private ListView<OnlineUser> lvUsers;
    @FXML private ListView<ChatMessage> lvMessages;
    @FXML private TextField tfMessage;
    @FXML private Button btnSend;
    @FXML private Label lblStatus;
    @FXML private Label lblTitle;
    @FXML private Button btnLogout;

    // ==================== ДАННЫЕ ====================
    private final ObservableList<OnlineUser> users = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    private String myUsername = "";
    private String privatePeer = null; // null = глобальный чат

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    @FXML
    public void initialize() {
        lvUsers.setItems(users);
        lvMessages.setItems(messages);

        // Кастомный рендер для сообщений
        lvMessages.setCellFactory(lv -> new MessageCell());

        // Кастомный рендер для пользователей
        lvUsers.setCellFactory(lv -> new UserCell());

        // Клик по пользователю — открыть личный чат
        lvUsers.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                OnlineUser user = lvUsers.getSelectionModel().getSelectedItem();
                if (user != null && !user.getUsername().equals(myUsername)) {
                    openPrivateChat(user.getUsername());
                }
            }
        });

        // Enter для отправки
        tfMessage.setOnAction(e -> onSend());

        // Настраиваем обработку пакетов
        SocketManager.getInstance().setPacketListener(json ->
                Platform.runLater(() -> handlePacket(json)));

        SocketManager.getInstance().setDisconnectListener(() ->
                Platform.runLater(this::onDisconnected));

        // Загружаем историю и список пользователей
        SocketManager.getInstance().requestGlobalHistory();
        SocketManager.getInstance().requestUserList();

        // Получаем имя из заголовка Stage
        Platform.runLater(() -> {
            String title = MainApp.getPrimaryStage().getTitle();
            if (title.contains("—")) {
                myUsername = title.substring(title.indexOf("—") + 2).trim();
                lblStatus.setText("Вошли как: " + myUsername);
            }
        });
    }

    // ==================== ОБРАБОТКА ПАКЕТОВ ====================

    private void handlePacket(JSONObject json) {
        String type = json.optString("type");

        switch (type) {
            case Packet.LOGIN_SUCCESS -> {
                myUsername = json.optString("username");
                lblStatus.setText("Вошли как: " + myUsername);
            }

            case Packet.GLOBAL_MESSAGE -> {
                if (privatePeer == null) { // Показываем только если в глобальном чате
                    ChatMessage msg = parseGlobalMsg(json);
                    messages.add(msg);
                    scrollToBottom();
                }
            }

            case Packet.PRIVATE_MESSAGE -> {
                ChatMessage msg = parsePrivateMsg(json);
                String sender = msg.getSenderUsername();
                String receiver = msg.getReceiverUsername();

                // Показываем если это наш приватный чат
                if (privatePeer != null &&
                        (sender.equals(privatePeer) || receiver.equals(privatePeer))) {
                    messages.add(msg);
                    scrollToBottom();
                } else if (!sender.equals(myUsername)) {
                    // Уведомление о новом личном сообщении
                    showNotification("Новое сообщение от " + sender);
                }
            }

            case Packet.USER_LIST -> {
                users.clear();
                JSONArray arr = json.optJSONArray("users");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject u = arr.getJSONObject(i);
                        users.add(new OnlineUser(
                                u.optInt("id"),
                                u.optString("username"),
                                u.optBoolean("online", true)));
                    }
                }
                updateTitle();
            }

            case Packet.USER_JOINED -> {
                String username = json.optString("username");
                if (users.stream().noneMatch(u -> u.getUsername().equals(username))) {
                    users.add(new OnlineUser(json.optInt("userId"), username, true));
                }
                updateTitle();
                showStatusMsg("★ " + username + " присоединился к чату");
            }

            case Packet.USER_LEFT -> {
                String username = json.optString("username");
                users.removeIf(u -> u.getUsername().equals(username));
                updateTitle();
                showStatusMsg("✗ " + username + " покинул чат");
            }

            case Packet.HISTORY_RESPONSE -> {
                messages.clear();
                JSONArray arr = json.optJSONArray("messages");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        boolean isGlobal = m.optBoolean("isGlobal", true);
                        messages.add(isGlobal ? parseGlobalMsg(m) : parsePrivateMsg(m));
                    }
                }
                scrollToBottom();
            }

            case Packet.NOTIFICATION -> showNotification(json.optString("content"));

            case Packet.ERROR -> showStatusMsg("⚠ " + json.optString("message"));
        }
    }

    // ==================== ДЕЙСТВИЯ ====================

    @FXML
    private void onSend() {
        String text = tfMessage.getText().trim();
        if (text.isEmpty()) return;

        if (privatePeer == null) {
            SocketManager.getInstance().sendGlobalMessage(text);
        } else {
            SocketManager.getInstance().sendPrivateMessage(privatePeer, text);
        }
        tfMessage.clear();
        tfMessage.requestFocus();
    }

    @FXML
    private void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Вы хотите выйти из аккаунта?",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Выход");
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            SocketManager.getInstance().sendLogout();
            SocketManager.getInstance().disconnect();
            try {
                MainApp.showLogin();
            } catch (Exception e) {
                System.exit(0);
            }
        }
    }

    /** Переключиться в глобальный чат */
    @FXML
    private void onGlobalChat() {
        privatePeer = null;
        messages.clear();
        lblTitle.setText("Общий чат");
        SocketManager.getInstance().requestGlobalHistory();
    }

    /** Открыть личный чат с пользователем */
    private void openPrivateChat(String username) {
        privatePeer = username;
        messages.clear();
        lblTitle.setText("Личный чат: " + username);
        SocketManager.getInstance().requestPrivateHistory(username);
    }

    private void onDisconnected() {
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "Соединение с сервером разорвано. Войти снова?",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Отключено");
        alert.setHeaderText("Соединение потеряно");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                MainApp.showLogin();
            } catch (Exception e) {
                System.exit(0);
            }
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ====================

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (!messages.isEmpty()) {
                lvMessages.scrollTo(messages.size() - 1);
            }
        });
    }

    private void updateTitle() {
        lblTitle.setText(privatePeer == null
                ? "Общий чат"
                : "Личный чат: " + privatePeer);
        lblStatus.setText("Вошли как: " + myUsername +
                " | Онлайн: " + users.size());
    }

    private void showStatusMsg(String msg) {
        // Добавляем системное сообщение в список
        Platform.runLater(() -> {
            // Просто обновляем статус бар
            lblStatus.setText(msg);
        });
    }

    private void showNotification(String msg) {
        // Простое уведомление через Alert (можно заменить на TrayIcon)
        Platform.runLater(() -> lblStatus.setText("📩 " + msg));
    }

    private ChatMessage parseGlobalMsg(JSONObject j) {
        return new ChatMessage(
                j.optInt("senderId"),
                j.optString("senderUsername"),
                j.optString("content"),
                j.optLong("timestamp"),
                true
        );
    }

    private ChatMessage parsePrivateMsg(JSONObject j) {
        return new ChatMessage(
                j.optInt("senderId"),
                j.optString("senderUsername"),
                j.optInt("receiverId"),
                j.optString("receiverUsername"),
                j.optString("content"),
                j.optLong("timestamp")
        );
    }

    // ==================== INNER CLASSES (кастомные ячейки) ====================

    /** Ячейка сообщения с пузырьком */
    private class MessageCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                return;
            }

            boolean mine = msg.isMine(myUsername);

            // Текст сообщения
            Text text = new Text(msg.getContent());
            text.setWrappingWidth(350);
            text.setFill(mine ? Color.WHITE : Color.BLACK);

            // Время
            Text time = new Text("  " + msg.getFormattedTime());
            time.setFill(mine ? Color.web("#e0e0e0") : Color.GRAY);
            time.setStyle("-fx-font-size: 10px;");

            // Пузырёк
            HBox bubble = new HBox(new TextFlow(text, time));
            bubble.setPadding(new Insets(8, 12, 8, 12));
            bubble.setMaxWidth(380);

            if (mine) {
                // Моё сообщение: синий пузырёк справа
                bubble.setStyle("-fx-background-color: #1976D2; -fx-background-radius: 16 4 16 16;");
            } else {
                // Чужое сообщение: серый пузырёк слева + имя отправителя
                String senderLabel = msg.getSenderUsername() + ": ";
                Text sender = new Text(senderLabel);
                sender.setFill(Color.web("#1565C0"));
                sender.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                bubble.getChildren().add(0, new TextFlow(sender));
                bubble.setStyle("-fx-background-color: #E3F2FD; -fx-background-radius: 4 16 16 16;");
            }

            HBox container = new HBox(bubble);
            container.setPadding(new Insets(3, 8, 3, 8));
            container.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            setGraphic(container);
            setStyle("-fx-background-color: transparent;");
        }
    }

    /** Ячейка пользователя в списке */
    private class UserCell extends ListCell<OnlineUser> {
        @Override
        protected void updateItem(OnlineUser user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }

            // Аватар (первая буква)
            Label avatar = new Label(String.valueOf(user.getUsername().charAt(0)).toUpperCase());
            avatar.setStyle(
                    "-fx-background-color: #BBDEFB; -fx-text-fill: #1565C0; " +
                    "-fx-background-radius: 20; -fx-font-weight: bold; " +
                    "-fx-min-width: 36; -fx-min-height: 36; -fx-alignment: center;");

            // Имя пользователя
            Label name = new Label(user.getUsername().equals(myUsername)
                    ? user.getUsername() + " (Вы)" : user.getUsername());
            name.setStyle("-fx-font-size: 13px;");

            // Индикатор онлайн
            Label status = new Label("● онлайн");
            status.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10px;");

            VBox info = new VBox(2, name, status);
            HBox row = new HBox(10, avatar, info);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));

            setGraphic(row);
            setStyle("-fx-background-color: transparent;");

            // Двойной клик = личный чат
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !user.getUsername().equals(myUsername)) {
                    openPrivateChat(user.getUsername());
                }
            });
        }
    }
}

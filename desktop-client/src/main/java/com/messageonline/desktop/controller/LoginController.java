package com.messageonline.desktop.controller;

import com.messageonline.desktop.MainApp;
import com.messageonline.desktop.model.Packet;
import com.messageonline.desktop.network.SocketManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/**
 * Контроллер экрана входа / регистрации.
 *
 * Управляет двумя вкладками: "Вход" и "Регистрация".
 */
public class LoginController {

    // ==================== FXML элементы ====================
    @FXML private TabPane tabPane;

    // Вкладка входа
    @FXML private TextField tfLoginHost;
    @FXML private TextField tfLoginPort;
    @FXML private TextField tfLoginUsername;
    @FXML private PasswordField pfLoginPassword;
    @FXML private Button btnLogin;
    @FXML private Label lblLoginStatus;
    @FXML private ProgressIndicator loginProgress;

    // Вкладка регистрации
    @FXML private TextField tfRegHost;
    @FXML private TextField tfRegPort;
    @FXML private TextField tfRegUsername;
    @FXML private TextField tfRegEmail;
    @FXML private PasswordField pfRegPassword;
    @FXML private PasswordField pfRegPasswordConfirm;
    @FXML private Button btnRegister;
    @FXML private Label lblRegStatus;
    @FXML private ProgressIndicator regProgress;

    @FXML
    public void initialize() {
        loginProgress.setVisible(false);
        regProgress.setVisible(false);
        lblLoginStatus.setText("");
        lblRegStatus.setText("");

        // Настраиваем слушатель входящих пакетов
        SocketManager.getInstance().setPacketListener(json -> {
            String type = json.optString("type");
            Platform.runLater(() -> handlePacket(type, json.optString("message", "")));
        });
    }

    /** Обработать пакет от сервера */
    private void handlePacket(String type, String message) {
        switch (type) {
            case Packet.LOGIN_SUCCESS -> {
                String username = getLoginField(tfLoginUsername);
                loginProgress.setVisible(false);
                btnLogin.setDisable(false);
                try {
                    MainApp.showChat(username);
                } catch (Exception e) {
                    showLoginError("Ошибка открытия чата");
                }
            }
            case Packet.LOGIN_FAIL -> {
                loginProgress.setVisible(false);
                btnLogin.setDisable(false);
                showLoginError(message);
            }
            case Packet.REGISTER_SUCCESS -> {
                regProgress.setVisible(false);
                btnRegister.setDisable(false);
                lblRegStatus.setStyle("-fx-text-fill: green;");
                lblRegStatus.setText("Регистрация успешна! Теперь войдите.");
                tabPane.getSelectionModel().select(0); // Переключаем на вкладку входа
            }
            case Packet.REGISTER_FAIL -> {
                regProgress.setVisible(false);
                btnRegister.setDisable(false);
                showRegError(message);
            }
            case Packet.ERROR -> showLoginError(message);
        }
    }

    @FXML
    private void onLogin() {
        String host     = tfLoginHost.getText().trim();
        String portStr  = tfLoginPort.getText().trim();
        String username = tfLoginUsername.getText().trim();
        String password = pfLoginPassword.getText();

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showLoginError("Заполните все поля");
            return;
        }

        int port = parsePort(portStr, 8888);
        setLoginLoading(true);

        // Подключаемся и логинимся в фоновом потоке
        new Thread(() -> {
            boolean ok = SocketManager.getInstance().connect(host, port);
            if (ok) {
                SocketManager.getInstance().sendLogin(username, password);
            } else {
                Platform.runLater(() -> {
                    setLoginLoading(false);
                    showLoginError("Не удалось подключиться к серверу " + host + ":" + port);
                });
            }
        }, "Connect-Thread").start();
    }

    @FXML
    private void onRegister() {
        String host     = tfRegHost.getText().trim();
        String portStr  = tfRegPort.getText().trim();
        String username = tfRegUsername.getText().trim();
        String email    = tfRegEmail.getText().trim();
        String password = pfRegPassword.getText();
        String confirm  = pfRegPasswordConfirm.getText();

        if (host.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showRegError("Заполните все поля");
            return;
        }
        if (username.length() < 3) { showRegError("Имя минимум 3 символа"); return; }
        if (!email.contains("@")) { showRegError("Неверный email"); return; }
        if (password.length() < 4) { showRegError("Пароль минимум 4 символа"); return; }
        if (!password.equals(confirm)) { showRegError("Пароли не совпадают"); return; }

        int port = parsePort(portStr, 8888);
        setRegLoading(true);

        new Thread(() -> {
            boolean ok = SocketManager.getInstance().connect(host, port);
            if (ok) {
                SocketManager.getInstance().sendRegister(username, email, password);
            } else {
                Platform.runLater(() -> {
                    setRegLoading(false);
                    showRegError("Не удалось подключиться к серверу");
                });
            }
        }, "Connect-Thread").start();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ====================

    private void setLoginLoading(boolean loading) {
        btnLogin.setDisable(loading);
        loginProgress.setVisible(loading);
        if (loading) lblLoginStatus.setText("Подключение...");
    }

    private void setRegLoading(boolean loading) {
        btnRegister.setDisable(loading);
        regProgress.setVisible(loading);
    }

    private void showLoginError(String msg) {
        lblLoginStatus.setStyle("-fx-text-fill: red;");
        lblLoginStatus.setText(msg);
    }

    private void showRegError(String msg) {
        lblRegStatus.setStyle("-fx-text-fill: red;");
        lblRegStatus.setText(msg);
    }

    private int parsePort(String str, int defaultPort) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private String getLoginField(TextField tf) {
        return tf.getText().trim();
    }
}

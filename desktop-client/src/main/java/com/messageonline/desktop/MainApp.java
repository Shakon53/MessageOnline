package com.messageonline.desktop;

import com.messageonline.desktop.network.SocketManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Главный класс JavaFX приложения.
 *
 * Точка входа — метод main().
 * При запуске показывает окно входа (login.fxml).
 */
public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        stage.setTitle("MessageOnline");
        stage.setResizable(false);

        // Показываем экран входа
        showLogin();

        // При закрытии — отключаемся от сервера
        stage.setOnCloseRequest(e -> {
            SocketManager.getInstance().sendLogout();
            SocketManager.getInstance().disconnect();
            Platform.exit();
        });

        stage.show();
    }

    /** Перейти на экран входа */
    public static void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/messageonline/desktop/login.fxml"));
        Scene scene = new Scene(loader.load(), 420, 520);
        scene.getStylesheets().add(
                MainApp.class.getResource("/com/messageonline/desktop/css/style.css")
                        .toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("MessageOnline — Вход");
        primaryStage.centerOnScreen();
    }

    /** Перейти в главный чат */
    public static void showChat(String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/messageonline/desktop/chat.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);
        scene.getStylesheets().add(
                MainApp.class.getResource("/com/messageonline/desktop/css/style.css")
                        .toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("MessageOnline — " + username);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.centerOnScreen();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

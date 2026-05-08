/**
 * Модульный дескриптор для JavaFX Desktop клиента.
 * Объявляет зависимости от JavaFX модулей и открывает пакеты для FXML.
 */
module com.messageonline.desktop {

    // Зависимости JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;

    // JSON библиотека
    requires org.json;

    // Открываем контроллеры для FXML (reflection)
    opens com.messageonline.desktop.controller to javafx.fxml;

    // Открываем модели для JavaFX (PropertyBeans, ObservableLists)
    opens com.messageonline.desktop.model to javafx.base;

    // Экспортируем главный пакет
    exports com.messageonline.desktop;
    exports com.messageonline.desktop.controller;
    exports com.messageonline.desktop.model;
    exports com.messageonline.desktop.network;
    exports com.messageonline.desktop.util;
}

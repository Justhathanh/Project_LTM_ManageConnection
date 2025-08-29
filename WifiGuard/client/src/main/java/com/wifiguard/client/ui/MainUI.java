package com.wifiguard.client.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainUI extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        Scene scene = new Scene(fxml.load(), 900, 560);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("WifiGuard");
        stage.setScene(scene);
        stage.show();
    }
}
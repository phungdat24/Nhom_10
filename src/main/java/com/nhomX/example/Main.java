package com.nhomX.example;

import java.io.IOException;

import com.nhomX.example.utils.SceneSwitcher;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {

        SceneSwitcher.mainStage = primaryStage;
        FXMLLoader fxmlLoader =
                new FXMLLoader(Main.class.getResource("/com/nhomX/example/fxml/dashboard.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1024, 700);
        primaryStage.setTitle("Auction Project");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

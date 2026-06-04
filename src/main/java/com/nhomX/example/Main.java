package com.nhomX.example;

import java.io.IOException;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        AuctionClient client = new AuctionClient("Guest");
        client.connect("10.11.18.139", 8080);
        // Lưu vào SessionManager để dùng xuyên suốt app
        SessionManager.getInstance().setAuctionClient(client);
        SceneSwitcher.mainStage = primaryStage;
        FXMLLoader fxmlLoader =
                new FXMLLoader(Main.class.getResource("/com/nhomX/example/fxml/client/dashboard.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1024, 700);
        primaryStage.setTitle("Auction Project");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

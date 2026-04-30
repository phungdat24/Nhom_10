package com.nhomX.example.utils;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneSwitcher {
    public static void sceneSwitcher(ActionEvent event, String fxmlPath){
        try {
            Parent root = FXMLLoader.load(SceneSwitcher.class.getResource(fxmlPath));

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.show();
        }catch (IOException e){
            System.out.println("Lỗi không tìm thấy file giao diện: " + fxmlPath);
            e.printStackTrace();
        }
    }
}

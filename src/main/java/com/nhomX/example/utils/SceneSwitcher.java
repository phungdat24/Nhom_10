package com.nhomX.example.utils;

import com.nhomX.example.controller.MainController;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneSwitcher {

    public static Stage mainStage;

    public static void switchScene(ActionEvent event, String fxmlPath){
        try {
            // 1. Tải phần giao diện mới (.fxml)
            Parent newRoot = FXMLLoader.load(SceneSwitcher.class.getResource(fxmlPath));

            // 2. Lấy Scene HIỆN TẠI từ cái nút vừa bấm
            Scene currentScene = ((Node) event.getSource()).getScene();

            // 3. Thay lõi (root) của Scene hiện tại bằng giao diện mới
            currentScene.setRoot(newRoot);

        } catch (IOException e){
            System.out.println("Lỗi không tìm thấy file giao diện: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public static void switchScene(String fxmlPath){
        try {
            // 1. Tải phần giao diện mới (.fxml)
            Parent newRoot = FXMLLoader.load(SceneSwitcher.class.getResource(fxmlPath));

            // 2. Kiểm tra nếu mainStage đã có Scene thì chỉ thay Root (giữ nguyên Fullscreen)
            if (mainStage.getScene() != null) {
                mainStage.getScene().setRoot(newRoot);
            } else {
                // Đề phòng trường hợp gọi hàm này lúc mới khởi động app, chưa có Scene nào
                mainStage.setScene(new Scene(newRoot));
                mainStage.show();
            }

        } catch (IOException e){
            System.out.println("Lỗi không tìm thấy file giao diện: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Hàm mới: Chỉ thay đổi nội dung phần Center của MainLayout
     */
    public static void loadContent(String fxmlPath) {
        if (MainController.instance != null) {
            MainController.instance.loadView(fxmlPath);
        } else {
            System.err.println("Lỗi: MainController chưa được khởi tạo!");
        }
    }
}
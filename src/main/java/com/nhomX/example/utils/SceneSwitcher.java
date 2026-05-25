package com.nhomX.example.utils;

import com.nhomX.example.controller.client.MainDashBoardController;
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
        if (MainDashBoardController.instance != null) {
            MainDashBoardController.instance.loadView(fxmlPath);
        } else {
            System.err.println("Lỗi: MainController chưa được khởi tạo!");
        }
    }
    public static void switchSceneInline(Node activeNode, String fxmlPath) {
        try {
            // 1. Tải file FXML mới
            FXMLLoader loader = new FXMLLoader(SceneSwitcher.class.getResource(fxmlPath));
            Parent root = loader.load();

            // 2. Lấy ra Stage hiện tại từ Node đang hoạt động
            Stage currentStage = (Stage) activeNode.getScene().getWindow();

            // 3. Thay thế ruột (Root) của Scene hiện tại thay vì đẻ Stage mới
            currentStage.getScene().setRoot(root);
            currentStage.sizeToScene(); // Tự động co giãn vừa vặn giao diện mới
            currentStage.centerOnScreen(); // Căn giữa màn hình

        } catch (IOException e) {
            System.err.println("❌ Lỗi nghiêm trọng: Không thể chuyển cảnh sang " + fxmlPath);
            e.printStackTrace();
        }
    }
}
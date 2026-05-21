package com.nhomX.example.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public class AlertUtils {
    // Hiện thông báo lỗi:
    public static void showError(String title, String content){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    // Hiện thông tin:
    public static void showSuccess(String title, String content){
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
    // Hiện cảnh bao:
    public static void showWarning(String title, String content){
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    // MỚI THÊM: Hiện hộp thoại xác nhận (Trả về kết quả True/False)
    // =======================================================
    public static boolean showConfirmation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        // showAndWait() sẽ trả về một Optional chứa nút mà người dùng vừa bấm
        Optional<ButtonType> result = alert.showAndWait();

        // Kiểm tra xem người dùng có bấm nút và nút đó có phải là OK hay không
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}

package com.nhomX.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {
    @FXML
    void register(ActionEvent event){
        System.out.println("Vừa bấm nut đăng ký");
    }

    @FXML private TextField     userName;   // Họ tên
    @FXML private TextField     account;    // Email
    @FXML private PasswordField password;   // Mật khẩu
    @FXML private PasswordField password1;  // Xác nhận mật khẩu

    // ============================================================
    // Xử lý nút "Đăng ký"
    // ============================================================
    @FXML
    void signUp(ActionEvent event) {
        String name     = userName.getText().trim();
        String email    = account.getText().trim();
        String pass     = password.getText().trim();
        String passConf = password1.getText().trim();

        // 1. Kiểm tra trống
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || passConf.isEmpty()) {
            showAlert("Lỗi!", "Vui lòng điền đầy đủ tất cả các trường!");
            return;
        }

        // 2. Kiểm tra mật khẩu khớp
        if (!pass.equals(passConf)) {
            showAlert("Lỗi!", "Mật khẩu xác nhận không khớp!");
            return;
        }

        // 3. Kiểm tra độ dài mật khẩu
        if (pass.length() < 6) {
            showAlert("Lỗi!", "Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }

        // 4. TODO: Gửi thông tin lên Server để lưu vào DB
        // (Hiện tại chưa có server, tạm thời thông báo thành công)
        System.out.println("Đăng ký thành công: " + email);
        showAlert("Thành công!", "Tài khoản đã được tạo. Vui lòng đăng nhập!");

        // 5. Chuyển về màn hình Login
        goToLogin(event);
    }

    // ============================================================
    // Hyperlink "Đã có tài khoản? Đăng nhập ngay" → quay về Login
    // ============================================================
    @FXML
    void handleBackToLogin(ActionEvent event) {
        goToLogin(event);
    }

    // ============================================================
    // Hàm chuyển về Login (dùng chung)
    // ============================================================
    private void goToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/nhomX/example/fxml/login.fxml")
            );
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // Hiển thị popup thông báo
    // ============================================================
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

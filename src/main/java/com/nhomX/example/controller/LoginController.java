package com.nhomX.example.controller;

import com.nhomX.example.controller.SessionManager;
import com.nhomX.example.model.User;
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

public class LoginController {

    @FXML private TextField     account;
    @FXML private PasswordField password;

    @FXML
    void Login(ActionEvent event) {
        String email = account.getText().trim();
        String pass  = password.getText().trim();

        // Kiểm tra trống
        if (email.isEmpty() || pass.isEmpty()) {
            showAlert("Lỗi!", "Vui lòng nhập đầy đủ email và mật khẩu!");
            return;
        }

        // ---- Tạm thời dùng tài khoản cứng để test ----
        // (Sau này thay bằng gọi API/Server)
        if (email.equals("admin") && pass.equals("123")) {

            // 1. Lưu thông tin vào SessionManager
            // Tạm thời tạo User giả để test — sau này thay bằng User trả về từ Server
            User fakeUser = new User();
            fakeUser.setUserName("Admin");
            fakeUser.setUserId(email);
            SessionManager.getInstance().login(fakeUser);

            System.out.println("Đăng nhập thành công! Xin chào: Admin");

            // 2. Chuyển về Dashboard
            try {
                goToDashboard(event);
            } catch (IOException e) {
                showAlert("Lỗi hệ thống!", "Không thể mở Dashboard.");
                e.printStackTrace();
            }

        } else {
            showAlert("Đăng nhập thất bại", "Email hoặc mật khẩu không chính xác.");
        }
    }

    private void goToDashboard(ActionEvent event) throws IOException {
        Parent root = FXMLLoader.load(
                getClass().getResource("/com/nhomX/example/fxml/dashboard.fxml")
        );
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    @FXML
    void handleRegister(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/com/nhomX/example/fxml/RegisterView.fxml")
            );
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

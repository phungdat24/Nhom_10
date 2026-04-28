package com.nhomX.example.controller;

import com.nhomX.example.repository.UserRepository;
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
    @FXML
    private TextField account;
    @FXML
    private PasswordField password;

    private UserRepository userRepository;

    public LoginController(){
        // Chờ DB
    }
    @FXML
    // Method khi bấm nút Login
    void Login(ActionEvent event){
        String email = account.getText();
        String pass = password.getText();
        // Kiểm tra xem có trống không
        if(email.isEmpty() || pass.isEmpty()){
            showAlert("Lỗi!", "Vui lòng nhập đúng email và mật khẩu!");
            return;
        }
        if(email.equals("admin") && pass.equals("123")){
            System.out.println("Đăng nhập thành công!");
            // Chuyển sang DashBoard
            try{
                goToDashboard(event);
            }catch (IOException e) {
                showAlert("Lỗi hệ thống!","404");
                e.printStackTrace();
            }
        }else{
            showAlert("Đăng nhập thất bại", "Email hoặc mật khẩu không chính xác.");
        }
    }
    private void goToDashboard(ActionEvent event) throws IOException{
        Parent dashboardRoot = FXMLLoader.load(getClass().getResource("/com/nhomX/example/fxml/dashboard.fxml"));

        // Lấy Stage (cửa sổ) hiện tại từ sự kiện click chuột
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

        // Đặt giao diện mới vào cửa sổ và hiển thị
        stage.setScene(new Scene(dashboardRoot));
        stage.show();
    }
    // 5. Hàm hiển thị thông báo (Popup)
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}
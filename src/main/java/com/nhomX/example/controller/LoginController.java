package com.nhomX.example.controller;

import com.nhomX.example.controller.SessionManager;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;
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

    private UserRepository userRepository = new UserRepositoryImpl();

    @FXML private TextField     account;
    @FXML private PasswordField password;

    @FXML
    // Method khi bấm nút Login
    void login(ActionEvent event){

        String email = account.getText();
        String pass = password.getText();

        User loggedInUser = userRepository.login(email, pass);

        // Kiểm tra xem có trống không
        if(loggedInUser == null){
            showAlert("Lỗi!", "Vui lòng nhập đúng email và mật khẩu!");
            return;
        }
        if(loggedInUser != null){
            System.out.println("Đăng nhập thành công!");

            SessionManager.getInstance().login(loggedInUser);

            goToDashboard(event);
        }else{
            showAlert("Đăng nhập thất bại", "Email hoặc mật khẩu không chính xác.");
        }
    }
    @FXML
    void handleRegister(ActionEvent event){
        try{
            Parent registerRoot = FXMLLoader.load(getClass().getResource("/com/nhomX/example/fxml/RegisterView.fxml"));
            Stage stage= (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(registerRoot));
            stage.show();
        }catch (IOException e){
            showAlert("Lỗi hệ thống", "Không thể chuyển sang màn hình đăng ký!");
            e.printStackTrace();
        }
    }
    private void goToDashboard(ActionEvent event){
        try {
            Parent dashboardRoot = FXMLLoader.load(getClass().getResource("/com/nhomX/example/fxml/dashboard.fxml"));

            // Lấy Stage (cửa sổ) hiện tại từ sự kiện click chuột
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Đặt giao diện mới vào cửa sổ và hiển thị
            stage.setScene(new Scene(dashboardRoot));
            stage.show();
        } catch (IOException e) {
            showAlert("Lỗi hệ thống", "Không thể chuyển sang màn hình chính");
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

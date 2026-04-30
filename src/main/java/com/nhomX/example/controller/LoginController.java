package com.nhomX.example.controller;

import com.nhomX.example.model.User;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    private UserRepository userRepository = new UserRepositoryImpl();

    @FXML private TextField     account;
    @FXML private PasswordField password;

    @FXML
    // Method khi bấm nút Login
    void login(ActionEvent event){

        String email = account.getText();
        String pass = password.getText();

        // Kiểm tra xem có trống không
        if(email.isEmpty() || email.isEmpty()){
            AlertUtils.showWarning("Lỗi!", "Vui lòng nhập đầy đủ email và mật khẩu!");
            return;
        }
        // Gio mới gọi database
        User loggedInUser = userRepository.login(email, pass);

        if(loggedInUser != null){
            System.out.println("Đăng nhập thành công!");

            SessionManager.getInstance().login(loggedInUser);

            SceneSwitcher.switchScene(event,"/com/nhomX/example/fxml/dashboard.fxml");
        }else{
            AlertUtils.showError("Đăng nhập thất bại", "Email hoặc mật khẩu không chính xác.");
        }
    }
    @FXML
    void handleRegister(ActionEvent event){
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/RegisterView.fxml");
    }
}

package com.nhomX.example.controller;

import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
import com.nhomX.example.utils.ValidatorUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField     userName;   // Họ tên
    @FXML private TextField     account;    // Email
    @FXML private PasswordField password;   // Mật khẩu
    @FXML private PasswordField password1;  // Xác nhận mật khẩu

    @FXML
    void signUp(ActionEvent event) {
        String name     = userName.getText().trim();
        String email    = account.getText().trim();
        String pass     = password.getText().trim();
        String passConf = password1.getText().trim();

        // 1. Kiểm tra trống
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || passConf.isEmpty()) {
            AlertUtils.showWarning("Lỗi!", "Vui lòng điền đầy đủ thông tin!");
            return;
        }
        // Kiểm tra định dạng email
        if (!ValidatorUtils.isValidEmail(email)) {
            AlertUtils.showWarning("Sai định dạng", "Email không hợp lệ (Ví dụ đúng: abc@gmail.com)!");
            return;
        }
        // 2. Kiểm tra mật khẩu khớp
        if (!pass.equals(passConf)) {
            AlertUtils.showWarning("Lỗi!", "Mật khẩu xác nhận không khớp!");
            return;
        }

        // 3. Kiểm tra độ dài mật khẩu
        if (pass.length() < 6) {
            AlertUtils.showWarning("Mật khẩu yếu", "Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }
        // Mã hóa mật khẩu:
        String securedPass = SecurityUtils.hashPassword(pass);

        AuctionClient auctionClient;
        auctionClient=SessionManager.getInstance().getAuctionClient();

        if(auctionClient == null){
            AlertUtils.showError("Lỗi kết nối!", "Chưa kết nối được với Server!");
            return;
        }

        Object[] registerData = {email, securedPass, name, 0.0};

        Message registerMsg = new Message("REGISTER", registerData);

        // 6. Gửi lên Server và để Giao diện ở trạng thái chờ
        auctionClient.sendToServer(registerMsg);

        System.out.println("Đã gửi yêu cầu đăng ký lên Server...");

    }

    // Hyperlink "Đã có tài khoản? Đăng nhập ngay" → quay về Login
    @FXML
    void handleBackToLogin(ActionEvent event) {
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/login.fxml");
    }
}

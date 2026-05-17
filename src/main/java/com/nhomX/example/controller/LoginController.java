package com.nhomX.example.controller;

import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
import com.nhomX.example.utils.ValidatorUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController implements ServerEventListener {

    private AuctionClient auctionClient;

    @FXML private TextField     account;
    @FXML private PasswordField password;

    @FXML
    // Method khi bấm nút Login
    void login(ActionEvent event){

        String email = account.getText();
        String pass = password.getText();

        // Kiểm tra xem có trống không
        if(email.isEmpty() || pass.isEmpty()){
            AlertUtils.showWarning("Lỗi!", "Vui lòng nhập đầy đủ email và mật khẩu!");
            return;
        }
        // Chặn ngay nếu gõ linh tinh, không cần gửi lên Server làm gì cho nghẽn mạng
        if (!ValidatorUtils.isValidEmail(email)) {
            AlertUtils.showWarning("Sai định dạng", "Email không hợp lệ!");
            return;
        }
        String securedPass = SecurityUtils.hashPassword(pass);
        String[] loginData = {email, securedPass};
        Message loginMsg= new Message("LOGIN", loginData);

        auctionClient=SessionManager.getInstance().getAuctionClient();
        //Dành quyền kết nối
        auctionClient.setServerEventListener(this);

        auctionClient.sendToServer(loginMsg);

    }
    @FXML
    void handleRegister(ActionEvent event){
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/RegisterView.fxml");
    }

    @Override
    public void onLoginResult(boolean isSuccess, String message, User userData) {
        // AuctionClient của đã thực hiện Platform.runLater ở hàm handleServerMessage rồi:
        if (isSuccess && userData != null) {
            SessionManager.getInstance().login(userData);
            // 2. Báo cho kết nối mạng biết tên (dùng Email hoặc ID đều được)
            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client != null) {
                client.setUsername(userData.getUserName());
            }
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");
        } else {
            AlertUtils.showError("Đăng nhập thất bại", message);
        }
    }
    @FXML
    private void handleForgotPassword(ActionEvent event) {
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/ForgotPassword2.fxml");
    }
    @FXML
    public void handleBackToHome() {
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");
    }
}

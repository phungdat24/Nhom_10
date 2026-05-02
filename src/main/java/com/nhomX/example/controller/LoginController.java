package com.nhomX.example.controller;

import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
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
        if(email.isEmpty() || email.isEmpty()){
            AlertUtils.showWarning("Lỗi!", "Vui lòng nhập đầy đủ email và mật khẩu!");
            return;
        }
        String securedPass = SecurityUtils.hashPassword(pass);
        String[] loginData = {email, securedPass};
        Message loginMsg= new Message("LOGIN", loginData);

        auctionClient=SessionManager.getInstance().getAuctionClient();
        //Dành quyền kết nối
        auctionClient.setServerEventListener(this);

        auctionClient.sendToServer(loginMsg);
        auctionClient.sendToServer(loginMsg);

    }
    @FXML
    void handleRegister(ActionEvent event){
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/RegisterView.fxml");
    }

    @Override
    public void onLoginResult(boolean isSuccess, String message, User userData) {
        javafx.application.Platform.runLater(() ->{
            if(isSuccess && userData != null){
                SessionManager.getInstance().login(userData);
                // 2. Báo cho kết nối mạng biết tên (dùng Email hoặc ID đều được)
                AuctionClient client = SessionManager.getInstance().getAuctionClient();
                if (client != null) {
                    client.setUsername(userData.getUserName());
                }
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");
            }else {
                AlertUtils.showError("Đăng nhập thất bại", message);
            }
        });
    }
}

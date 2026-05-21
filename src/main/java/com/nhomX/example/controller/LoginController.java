package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
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
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements ServerEventListener , Initializable {

    private AuctionClient auctionClient;

    @FXML private TextField     account;
    @FXML private PasswordField password;
    @FXML private TextField passwordVisible;
    @FXML private Label togglePasswordIcon;
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Đồng bộ dữ liệu 2 chiều giữa ô Password và ô TextField
        if (password != null && passwordVisible != null) {
            passwordVisible.textProperty().bindBidirectional(password.textProperty());
        }
    }
    // Method khi bấm nút Login
    @FXML
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

        auctionClient= SessionManager.getInstance().getAuctionClient();
        if(auctionClient != null) {
            //Dành quyền kết nối
            auctionClient.setServerEventListener(this);

            auctionClient.sendToServer(loginMsg);
        }else {
            AlertUtils.showError("Lỗi kết nối", "Hệ thống chưa kết nối đến Server!");
        }

    }
    @FXML
    void handleRegister(ActionEvent event){
        // [REFACTOR 1]: Hủy đăng ký lắng nghe trước khi rời đi để chống Memory Leak
        if (auctionClient != null) auctionClient.setServerEventListener(null);
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
                // [REFACTOR 2]: Trả lại micro cho hệ thống trước khi vào màn hình chính
                auctionClient.setServerEventListener(null);
            }
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");
        } else {
            AlertUtils.showError("Đăng nhập thất bại", message);
        }
    }
    @FXML
    private void handleForgotPassword(ActionEvent event) {
        if (auctionClient != null) {
            auctionClient.setServerEventListener(null);
        }
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/ForgotPwEmail.fxml");
    }
    @FXML
    public void handleBackToHome() {
        if (auctionClient != null){
            auctionClient.setServerEventListener(null);
        }
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");
    }
    @FXML
    private void handleTogglePassword() {
        if (password.isVisible()) {
            // Đang ẩn -> Chuyển sang chế độ hiện chữ
            password.setVisible(false);
            password.setManaged(false); // Rút ô ẩn ra khỏi luồng Layout

            passwordVisible.setVisible(true);
            passwordVisible.setManaged(true); // Đưa ô chữ thường vào Layout

            togglePasswordIcon.setText("🙈"); // Đổi icon thành khỉ bịt mắt (hoặc 👁‍🗨)
        } else {
            // Đang hiện -> Chuyển về chế độ dấu chấm đen
            passwordVisible.setVisible(false);
            passwordVisible.setManaged(false);

            password.setVisible(true);
            password.setManaged(true);

            togglePasswordIcon.setText("👁");
        }
    }
}

package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
import com.nhomX.example.utils.ValidatorUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController implements ServerEventListener {

    @FXML
    private TextField userName;   // Họ tên
    @FXML
    private TextField account;    // Email
    @FXML
    private PasswordField password;   // Mật khẩu
    @FXML
    private PasswordField password1;  // Xác nhận mật khẩu
    @FXML
    private Button btnSignUp; // Dùng Node này làm điểm tựa lấy Stage
    private AuctionClient auctionClient;

    //   Khi bấm nút đăng nhập:
    @FXML
    void signUp(ActionEvent event) {
        String name = userName.getText().trim();
        String email = account.getText().trim();
        String pass = password.getText().trim();
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
        };
        // Mã hóa mật khẩu:
        String securedPass = SecurityUtils.hashPassword(pass);

        auctionClient = SessionManager.getInstance().getAuctionClient();

        if (auctionClient == null) {
            AlertUtils.showError("Lỗi kết nối!", "Chưa kết nối được với Server!");
            return;
        }
        //GIÀNH QUYỀN NGHE SÓNG CHO TRANG ĐĂNG KÝ
        auctionClient.setServerEventListener(this);
        Object[] registerData = {email, securedPass, name, 0L};
        Message registerMsg = new Message("REGISTER", registerData);
        // 6. Gửi lên Server và để Giao diện ở trạng thái chờ
        auctionClient.sendToServer(registerMsg);
        btnSignUp.setDisable(true); // Khóa nút tránh spam

        System.out.println("Đã gửi yêu cầu đăng ký lên Server...");

    }

    // Hyperlink "Đã có tài khoản? Đăng nhập ngay" → quay về Login
    @FXML
    void handleBackToLogin(ActionEvent event) {
        if (auctionClient != null) auctionClient.setServerEventListener(null); // Dọn rác
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/login/login.fxml");
    }

    @Override
    public void onRegisterResult(boolean isSuccess, String message) {
        btnSignUp.setDisable(false);
        Platform.runLater(()->{
            if (isSuccess) {
                if (auctionClient != null) auctionClient.setServerEventListener(null); // Dọn rác
                // Đăng ký thành công: Báo xanh và tự động chuyển về trang Login
                AlertUtils.showSuccess("Đăng ký thành công", message);
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/login.fxml");
            } else {
                AlertUtils.showError("Đăng ký thất bại", message);
            }
        });
    }

    // [BỔ SUNG]: Hàm tự động chạy khi nhận lệnh "SHOW_OTP_DIALOG" từ Server
    @Override
    public void onShowOtpDialog() {
            // Bắt buộc chạy trong Platform.runLater vì cuộc gọi đến từ luồng mạng
        Platform.runLater(() -> {
            btnSignUp.setDisable(false);
            // [QUAN TRỌNG]: BÁO CHO HỆ THỐNG BIẾT ĐÂY LÀ LUỒNG ĐĂNG KÝ
            SessionManager.getInstance().setTempEmail(account.getText().trim());
            SessionManager.getInstance().setCurrentFlow("REGISTER");
            System.out.println("CLIENT: Server báo đã gửi email OTP thành công. Tiến hành chuyển cảnh nội tuyến...");

            // Thực hiện chuyển cảnh ngay trên cửa sổ này sang giao diện 6 ô OTP
            SceneSwitcher.switchSceneInline(btnSignUp, "/com/nhomX/example/fxml/OTPContent.fxml");
        });
    }

    @FXML
    public void handleBackToHome() {
        if (auctionClient != null) auctionClient.setServerEventListener(null); // Dọn rác
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/client/dashboard.fxml");
    }
}

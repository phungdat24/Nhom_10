package com.nhomX.example.controller;

import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
import com.nhomX.example.utils.ValidatorUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RegisterController implements ServerEventListener {

    @FXML
    private TextField userName;   // Họ tên
    @FXML
    private TextField account;    // Email
    @FXML
    private PasswordField password;   // Mật khẩu
    @FXML
    private PasswordField password1;  // Xác nhận mật khẩu

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
        }
        // Mã hóa mật khẩu:
        String securedPass = SecurityUtils.hashPassword(pass);

        AuctionClient auctionClient;
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

        System.out.println("Đã gửi yêu cầu đăng ký lên Server...");

    }

    // Hyperlink "Đã có tài khoản? Đăng nhập ngay" → quay về Login
    @FXML
    void handleBackToLogin(ActionEvent event) {
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/login.fxml");
    }

    @Override
    public void onRegisterResult(boolean isSuccess, String message) {
        if (isSuccess) {
            // Đăng ký thành công: Báo xanh và tự động chuyển về trang Login
            AlertUtils.showSuccess("Đăng ký thành công", message);
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
        } else {
            // Đăng ký thất bại (ví dụ: trùng Email): Báo đỏ và đứng yên tại chỗ
            AlertUtils.showError("Đăng ký thất bại", message);
        }
    }

    // [BỔ SUNG]: Hàm tự động chạy khi nhận lệnh "SHOW_OTP_DIALOG" từ Server
    @Override
    public void onShowOtpDialog() {
        javafx.application.Platform.runLater(() -> {
            try {
                // Tải file giao diện Pop-up OTP mà em đã thiết kế
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/OtpDialog.fxml"));
                Parent root = loader.load();

                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL); // Khóa màn hình chính đằng sau lại
                stage.setTitle("Xác thực mã định danh OTP");
                stage.setScene(new Scene(root));
                stage.show();

            } catch (java.io.IOException e) {
                System.err.println("Lỗi hiển thị Pop-up OTP: " + e.getMessage());
            }
        });
    }
}

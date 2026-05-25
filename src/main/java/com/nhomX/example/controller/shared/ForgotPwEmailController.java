package com.nhomX.example.controller.shared;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.ValidatorUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ForgotPwEmailController implements ServerEventListener {
    @FXML private TextField emailField;
    @FXML private Label lblError;
    @FXML private Button btnSendOtp;

    private AuctionClient auctionClient;

    @FXML
    public void initialize() {
        // Ẩn nhãn báo lỗi khi mới mở giao diện
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    @FXML
    private void handleSendOtp(ActionEvent event) {
        String email = emailField.getText().trim();

        // 1. Kiểm tra rỗng
        if (email.isEmpty()) {
            showInlineError("Vui lòng nhập địa chỉ email!");
            return;
        }

        // 2. Kiểm tra định dạng (Chặn ngay ở Client để đỡ tốn băng thông Server)
        if (!ValidatorUtils.isValidEmail(email)) {
            showInlineError("Email không đúng định dạng!");
            return;
        }

        // 3. Chuẩn bị kết nối mạng
        auctionClient = SessionManager.getInstance().getAuctionClient();
        if (auctionClient != null) {
            // Giành quyền lắng nghe phản hồi từ Server
            auctionClient.setServerEventListener(this);

            // [TỐI ƯU UX]: Khóa nút bấm và đổi text để báo hiệu hệ thống đang xử lý
            // Tránh việc người dùng bấm liên tục tạo ra hàng tá Request gửi lên Server
            btnSendOtp.setDisable(true);
            btnSendOtp.setText("Đang gửi OTP...");
            lblError.setVisible(false);

            // 4. Đóng gói dữ liệu gửi đi
            String[] requestData = {email};
            Message msg = new Message("FORGOT_PASSWORD_REQUEST", requestData);
            auctionClient.sendToServer(msg);
        } else {
            AlertUtils.showError("Lỗi kết nối", "Không thể kết nối đến máy chủ.");
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        // [QUAN TRỌNG]: Hủy đăng ký lắng nghe trước khi rời đi để chống Memory Leak
        if (auctionClient != null) {
            auctionClient.setServerEventListener(null);
        }
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/client/login.fxml");
    }
    // XỬ LÝ LẮNG NGHE TỪ SERVER (ServerEventListener)
    @Override
    public void onForgotPasswordResult(boolean isSuccess, String message) {
        // Mọi thao tác cập nhật UI (Giao diện) đều phải nhét vào Platform.runLater
        Platform.runLater(() -> {
            // Mở khóa lại nút bấm
            btnSendOtp.setDisable(false);
            btnSendOtp.setText("📨   Gửi mã OTP");

            if (isSuccess) {
                // 1. Lưu tạm email vào Session để màn hình OTP (Step 2) có thể lấy ra dùng
                SessionManager.getInstance().setTempEmail(emailField.getText().trim());
                // 2. [QUAN TRỌNG]: BÁO CHO HỆ THỐNG BIẾT ĐÂY LÀ LUỒNG QUÊN MẬT KHẨU
                SessionManager.getInstance().setCurrentFlow("FORGOT_PASSWORD");

                // 3. Thông báo và chuyển cảnh
                AlertUtils.showSuccess("Thành công", "Mã OTP đã được gửi. Vui lòng kiểm tra hộp thư!");

                if (auctionClient != null) {
                    auctionClient.setServerEventListener(null); // Trả lại micro
                }

                // Thay đổi tên file FXML này cho khớp với tên màn hình OTP của nhóm em
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/client/OTPContent.fxml");
            } else {
                // Báo lỗi bằng dòng chữ đỏ phía dưới ô input
                showInlineError(message);
            }
        });
    }

    // Hàm tiện ích để hiển thị lỗi mượt mà ngay trên Giao diện
    private void showInlineError(String errorMsg) {
        lblError.setText(errorMsg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}

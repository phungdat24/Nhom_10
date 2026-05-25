package com.nhomX.example.controller.shared;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class OTPContentController implements Initializable , ServerEventListener {
    // Khai báo 6 ô nhập OTP
    @FXML
    private TextField otp1;
    @FXML
    private TextField otp2;
    @FXML
    private TextField otp3;
    @FXML
    private TextField otp4;
    @FXML
    private TextField otp5;
    @FXML
    private TextField otp6;
    @FXML
    private Label lblResendTimer;   // Thêm một Label cạnh nút gửi lại để hiện: (60s)
    @FXML
    private Hyperlink btnResend;
    @FXML
    private Button btnConfirm;
    @FXML
    private Label lblEmail;

    // Mảng chứa các ô OTP để dễ dàng dùng vòng lặp xử lý
    private TextField[] otpFields;
    private Timeline countdownTimeline;
    private int timeLeftSeconds = 60; // Thời gian chờ giữa 2 lần gửi lại (60 giây)

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Gom các ô vào mảng theo đúng thứ tự
        otpFields = new TextField[]{otp1, otp2, otp3, otp4, otp5, otp6};

        setupOtpInputLogic();
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if(client != null ){
            client.setServerEventListener(this);
        }
        startResendCountdown();
        // [THÊM MỚI]: LẤY EMAIL TỪ SESSION VÀ HIỂN THỊ LÊN GIAO DIỆN
        // =========================================================
        String savedEmail = SessionManager.getInstance().getTempEmail();
        if (savedEmail != null && !savedEmail.isEmpty()) {
            if (lblEmail != null) {
                // Che giấu một phần email cho chuyên nghiệp (Bảo mật thông tin)
                lblEmail.setText(maskEmail(savedEmail));
            }
        }
    }
    // Thêm hàm dọn dẹp chuyên nghiệp (Garbage Collection Helper)
    private void cleanup() {
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Tắt đồng hồ ngầm
        }
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(null); // Trả lại micro
        }
    }
    /**
     * Kích hoạt đồng hồ đếm ngược khóa nút gửi lại mã
     */
    private void startResendCountdown() {
        // [FIX BUG 2]: Bắt buộc phải DỪNG đồng hồ cũ trước khi tạo đồng hồ mới
        // Nếu không có dòng này, các luồng thời gian sẽ chạy đè lên nhau gây lỗi kẹt UI
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        // 1. TRẠNG THÁI BAN ĐẦU (Khi bắt đầu đếm ngược)
        // Ẩn Hyperlink
        btnResend.setVisible(false);
        btnResend.setManaged(false);
        // Hiện dòng chữ đếm giờ và reset lại màu mặc định
        lblResendTimer.setVisible(true);
        lblResendTimer.setManaged(true);
        lblResendTimer.setStyle("-fx-text-fill: rgba(255,220,160,0.50); -fx-font-size: 12px;");
        timeLeftSeconds = 60;       // Đặt lại thời gian chờ

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeLeftSeconds--;
            if (timeLeftSeconds > 0) {
                lblResendTimer.setText("Gửi lại mã sau: (" + timeLeftSeconds + "s)");
                // 2. HIỆU ỨNG NHẤP NHÁY KHI CÒN <= 10 GIÂY
                if (timeLeftSeconds <= 10) {
                    if (timeLeftSeconds % 2 == 0) {
                        // Giây chẵn: Đổi sang màu đỏ, in đậm
                        lblResendTimer.setStyle("-fx-text-fill: #ff4444; -fx-font-size: 12px; -fx-font-weight: bold;");
                    } else {
                        // Giây lẻ: Trả về màu mờ mặc định để tạo cảm giác chớp nháy
                        lblResendTimer.setStyle("-fx-text-fill: rgba(255,220,160,0.50); -fx-font-size: 12px;");
                    }
                }
            } else {
                // 3. KHI HẾT GIỜ (0s)
                countdownTimeline.stop();

                // Ẩn dòng chữ đếm giờ
                lblResendTimer.setVisible(false);
                lblResendTimer.setManaged(false);

                // HIỆN Hyperlink "Gửi lại ngay"
                btnResend.setVisible(true);
                btnResend.setManaged(true);
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    /**
     * Cài đặt logic: Tự động chuyển ô khi nhập, lùi lại khi xóa, và chỉ cho nhập số.
     */
    private void setupOtpInputLogic() {
        for (int i = 0; i < otpFields.length; i++) {
            TextField currentField = otpFields[i];
            final int currentIndex = i;

            // Lắng nghe sự thay đổi văn bản (Chỉ cho phép nhập số, tối đa 1 ký tự)
            currentField.textProperty().addListener((observable, oldValue, newValue) -> {
                // Nếu người dùng nhập ký tự không phải là số -> Lọc bỏ luôn
                if (!newValue.matches("\\d*")) {
                    currentField.setText(newValue.replaceAll("[^\\d]", ""));
                }

                // Nếu độ dài vượt quá 1 (ví dụ copy paste hoặc gõ nhanh) -> Cắt lấy ký tự đầu
                if (currentField.getText().length() > 1) {
                    currentField.setText(currentField.getText().substring(0, 1));
                }

                // Nếu nhập thành công 1 số và chưa phải ô cuối cùng -> Focus sang ô tiếp theo
                if (currentField.getText().length() == 1 && currentIndex < otpFields.length - 1) {
                    otpFields[currentIndex + 1].requestFocus();
                }
            });

            // Lắng nghe sự kiện gõ phím để xử lý nút Backspace (Xóa lùi)
            currentField.setOnKeyPressed((KeyEvent event) -> {
                if (event.getCode() == KeyCode.BACK_SPACE) {
                    // Nếu ô hiện tại đang trống và bấm Xóa -> Nhảy lùi về ô trước đó
                    if (currentField.getText().isEmpty() && currentIndex > 0) {
                        otpFields[currentIndex - 1].requestFocus();
                        // Tiện tay xóa luôn dữ liệu ở ô trước đó cho mượt
                        otpFields[currentIndex - 1].clear();
                    }
                }
            });
        }
    }

    /**
     * Xử lý sự kiện khi người dùng bấm nút Xác nhận (Verify)
     */
    @FXML
    private void handleConfirmOtp(ActionEvent event) {
        // Nối chuỗi từ 6 ô lại với nhau
        String otpCode = otp1.getText() + otp2.getText() + otp3.getText() +
                otp4.getText() + otp5.getText() + otp6.getText();

        // Kiểm tra xem đã nhập đủ 6 số chưa
        if (otpCode.length() < 6) {
            AlertUtils.showWarning("Lỗi nhập liệu", "Vui lòng nhập đầy đủ mã OTP 6 số!");
            return;
        }

        // Vô hiệu hóa nút bấm tạm thời để tránh user click đúp gửi lệnh 2 lần
        btnConfirm.setDisable(true);

        // Gửi OTP lên Server để kiểm tra (Khớp với case "VERIFY_REGISTER_OTP" trong ClientHandler)
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // Lấy lại email đang thao tác
            String targetEmail = SessionManager.getInstance().getTempEmail();

            // Đóng gói Email và OTP thành 1 mảng để gửi lên Server
            String[] payload = {targetEmail, otpCode};
            // LÔI BIỂN BÁO RA ĐỌC VÀ CHIA LUỒNG GỬI GÓI TIN LÊN SERVER
            // =======================================================
            String activeFlow = SessionManager.getInstance().getCurrentFlow();

            // Phân luồng: Nếu em set cờ là FORGOT_PASSWORD thì bắn gói tin khác
            if ("FORGOT_PASSWORD".equals(activeFlow)) {
                System.out.println("CLIENT: Gửi xác thực OTP QUÊN MẬT KHẨU...");
                client.sendToServer(new Message("VERIFY_FORGOT_PW_OTP", payload));
            } else {
                System.out.println("CLIENT: Gửi xác thực OTP ĐĂNG KÝ...");
                client.sendToServer(new Message("VERIFY_REGISTER_OTP", payload));
            }
        } else {
            AlertUtils.showError("Lỗi mạng", "Chưa kết nối đến Server!");
            btnConfirm.setDisable(false);
        }
    }

    /**
     * (Tùy chọn) Xử lý sự kiện khi người dùng bấm nút Gửi lại mã (Resend)
     */
    @FXML
    private void handleResendOtp(ActionEvent event) {
        // Clear hết các ô nhập liệu hiện tại
        for (TextField field : otpFields) {
            field.clear();
        }
        otp1.requestFocus(); // Đưa trỏ chuột về lại ô đầu tiên

        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // Phải lấy Email và Luồng hiện tại để báo cáo cho Server
            String targetEmail = SessionManager.getInstance().getTempEmail();
            String activeFlow = SessionManager.getInstance().getCurrentFlow();
            String[] payload = {targetEmail, activeFlow};
            System.out.println("CLIENT: Yêu cầu gửi lại mã OTP lên Server...");

            // [FIX BUG 1]: PHẢI CÓ LỆNH NÀY THÌ SERVER MỚI NHẬN ĐƯỢC YÊU CẦU!
            client.sendToServer(new Message("RESEND_OTP", payload));

            // Khởi động lại đồng hồ đếm ngược
            startResendCountdown();
        }
    }
    @FXML
    private void handleBack(ActionEvent event) {
        cleanup(); // Chống tràn RAM khi bấm nút Back

        // Đọc "Biển báo" từ Nguồn sự thật duy nhất
        String activeFlow = SessionManager.getInstance().getCurrentFlow();

        if ("FORGOT_PASSWORD".equals(activeFlow)) {
            // Đang quên pass thì phải lùi về màn hình nhập Email
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/Forgot/ForgotPwEmail.fxml");
        } else {
            // Đang đăng ký thì lùi về màn hình Đăng ký
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/RegisterView.fxml");
        }
    }
    // Lắng nghe kết quả từ Server trả về
    @Override
    public void onRegisterResult(boolean isSuccess, String message) {
        // Luôn chạy UI update trong luồng Platform.runLater
        Platform.runLater(() -> {
            btnConfirm.setDisable(false); // Mở khóa nút bấm

            if (isSuccess) {
                cleanup(); //Chống tràn RAM khi đky thành công
                AlertUtils.showSuccess("Thành công", message);
                // Chuyển thẳng về trang Đăng nhập sau khi OTP đúng
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/login.fxml");
            } else {
                AlertUtils.showError("Đăng ký thất bại", message);
                clearOtpFieldsForRetry();
            }
        });
    }
    // LUỒNG 2: XỬ LÝ KẾT QUẢ QUÊN MẬT KHẨU
    @Override
    public void onForgotPasswordResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            btnConfirm.setDisable(false); // Mở khóa nút bấm

            if (isSuccess) {
                cleanup();
                // Xác thực OTP thành công -> Chuyển sang Bước 3: Đặt mật khẩu mới
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/Forgot/ForgotPwNew.fxml");
            } else {
                AlertUtils.showError("Xác thực thất bại", message);
                // [FIX BUG]: Tiện tay xóa 6 ô OTP bắt nhập lại cho đẹp
                clearOtpFieldsForRetry();
            }
        });
    }
    // Hàm phụ trợ che giấu Email (VD: nguyenvanban@gmail.com -> ngu***@gmail.com)
    private String maskEmail(String email) {
        if (!email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 3) return name + "***@" + domain;
        return name.substring(0, 3) + "***@" + domain;
    }
    // Hàm phụ trợ chuyên dọn dẹp các ô OTP khi nhập sai (Clean Code)
    private void clearOtpFieldsForRetry() {
        for (TextField field : otpFields) {
            field.clear();
        }
        otpFields[0].requestFocus(); // Đưa con trỏ nhấp nháy về ô đầu tiên
    }
}

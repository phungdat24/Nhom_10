package com.nhomX.example.controller.shared;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import com.nhomX.example.utils.SecurityUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

public class ForgotPwNewController implements ServerEventListener {
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField newPasswordVisible; // Ô text ẩn dùng khi toggle
    @FXML
    private Label toggleNewPassword;
    @FXML
    private Label matchIndicator;
    @FXML
    private Label lblStrength;
    @FXML
    private Region bar1, bar2, bar3, bar4;
    @FXML
    private Label lblError;
    @FXML
    private Button btnConfirm;

    private boolean isPasswordVisible = false;
    private AuctionClient auctionClient;

    @FXML
    public void initialize() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        // [FIX BUG 1]: DÙNG ĐỒNG BỘ HAI CHIỀU THAY VÌ LẮNG NGHE LẶP VÒNG
        // Khi gõ vào ô ẩn, ô hiện sẽ tự đổi và ngược lại. Không bị nhảy con trỏ!
        // ========================================================
        newPasswordVisible.textProperty().bindBidirectional(newPasswordField.textProperty());
        // 1. Lắng nghe sự thay đổi của mật khẩu mới để tính toán độ mạnh
        newPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            newPasswordVisible.setText(newVal); // Đồng bộ với ô text ẩn
            updatePasswordStrength(newVal);
            checkPasswordsMatch();
        });
        // Lắng nghe ô xác nhận mật khẩu để hiện dấu tích xanh/đỏ
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> checkPasswordsMatch());
        // 2. Lắng nghe sự thay đổi của ô text ẩn (khi đang hiện mật khẩu)
        newPasswordVisible.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPasswordVisible) {
                newPasswordField.setText(newVal);
            }
        });

        // 3. Lắng nghe ô xác nhận mật khẩu để hiện dấu tích xanh/đỏ
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> checkPasswordsMatch());

        // 4. Kết nối Client
        auctionClient = SessionManager.getInstance().getAuctionClient();
        if (auctionClient != null) {
            auctionClient.setServerEventListener(this);
        }
    }
    // LOGIC GIAO DIỆN (UX)

    @FXML
    private void handleToggleNewPassword(MouseEvent mouseEvent) {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            toggleNewPassword.setText("🙈");
            newPasswordVisible.setVisible(true);
            newPasswordVisible.setManaged(true);
            newPasswordField.setVisible(false);
            newPasswordField.setManaged(false);
            // [TỐI ƯU UX]: Đưa con trỏ chuột về đúng vị trí cuối cùng sau khi chuyển đổi
            newPasswordVisible.requestFocus();
            newPasswordVisible.positionCaret(newPasswordVisible.getText().length());
        } else {
            toggleNewPassword.setText("👁");
            newPasswordVisible.setVisible(false);
            newPasswordVisible.setManaged(false);
            newPasswordField.setVisible(true);
            newPasswordField.setManaged(true);
            // [TỐI ƯU UX]: Đưa con trỏ chuột về đúng vị trí cuối cùng sau khi chuyển đổi
            newPasswordField.requestFocus();
            newPasswordField.positionCaret(newPasswordField.getText().length());
        }
    }

    private void updatePasswordStrength(String password) {
        int score = 0;
        if (password.length() >= 6) score++;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Za-z].*") && password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[!@#$%^&*()_+=\\-`~\\\\\\]\\[{}|';:/.,?><].*")) score++;

        // Reset màu các thanh bar
        String defaultColor = "-fx-background-color: rgba(255,255,255,0.12); -fx-background-radius: 3;";
        bar1.setStyle(defaultColor); bar2.setStyle(defaultColor);
        bar3.setStyle(defaultColor); bar4.setStyle(defaultColor);

        // Đổi màu tùy theo điểm
        if (score >= 1) { bar1.setStyle("-fx-background-color: #ff4444; -fx-background-radius: 3;"); lblStrength.setText("Yếu"); lblStrength.setStyle("-fx-text-fill: #ff4444;"); }
        if (score >= 2) { bar2.setStyle("-fx-background-color: #ff8800; -fx-background-radius: 3;"); lblStrength.setText("Trung bình"); lblStrength.setStyle("-fx-text-fill: #ff8800;"); }
        if (score >= 3) { bar3.setStyle("-fx-background-color: #00C851; -fx-background-radius: 3;"); lblStrength.setText("Mạnh"); lblStrength.setStyle("-fx-text-fill: #00C851;"); }
        if (score >= 4) { bar4.setStyle("-fx-background-color: #007E33; -fx-background-radius: 3;"); lblStrength.setText("Rất mạnh"); lblStrength.setStyle("-fx-text-fill: #007E33;"); }
        if (score == 0) { lblStrength.setText("—"); lblStrength.setStyle("-fx-text-fill: rgba(255,220,160,0.50);"); }
    }

    private void checkPasswordsMatch() {
        String pass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (confirm.isEmpty()) {
            matchIndicator.setText("");
        } else if (pass.equals(confirm)) {
            matchIndicator.setText("✔");
            matchIndicator.setStyle("-fx-text-fill: #00C851;"); // Màu xanh lá
        } else {
            matchIndicator.setText("✘");
            matchIndicator.setStyle("-fx-text-fill: #ff4444;"); // Màu đỏ
        }
    }

    // ==========================================
    // LOGIC KẾT NỐI SERVER
    // ==========================================

    @FXML
    private void handleConfirmNewPassword(ActionEvent event) {
        String newPass = newPasswordField.getText();
        String confirmPass = confirmPasswordField.getText();

        if (newPass.length() < 6) {
            showInlineError("Mật khẩu mới phải có ít nhất 6 ký tự!");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showInlineError("Mật khẩu xác nhận không khớp!");
            return;
        }

        btnConfirm.setDisable(true);
        lblError.setVisible(false);

        if (auctionClient != null) {
            // Lấy lại email từ SessionManager
            String targetEmail = SessionManager.getInstance().getTempEmail();

            // [BẢO MẬT]: Băm mật khẩu ra thành chuỗi mã hóa trước khi gửi đi
            String securedPass = SecurityUtils.hashPassword(newPass);

            // Gửi gói tin cập nhật mật khẩu lên Server
            String[] payload = {targetEmail, securedPass};
            auctionClient.sendToServer(new Message("RESET_PASSWORD", payload));
            System.out.println("CLIENT: Đang gửi yêu cầu đổi mật khẩu mới...");
        } else {
            AlertUtils.showError("Lỗi mạng", "Chưa kết nối đến máy chủ.");
            btnConfirm.setDisable(false);
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        cleanup();
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/login/login.fxml");
    }

    private void cleanup() {
        if (auctionClient != null) auctionClient.setServerEventListener(null);
        // Hủy bỏ email tạm vì quá trình đã kết thúc hoặc bị hủy
        SessionManager.getInstance().setTempEmail(null);
    }

    // Lắng nghe kết quả từ Server (Tái sử dụng hàm onForgotPasswordResult cho tiện)
    @Override
    public void onForgotPasswordResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            btnConfirm.setDisable(false);
            if (isSuccess) {
                cleanup();
                AlertUtils.showSuccess("Đổi mật khẩu thành công", "Mật khẩu của bạn đã được cập nhật. Vui lòng đăng nhập lại!");
                SceneSwitcher.switchScene("/com/nhomX/example/fxml/login/login.fxml");
            } else {
                showInlineError(message);
            }
        });
    }

    private void showInlineError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}

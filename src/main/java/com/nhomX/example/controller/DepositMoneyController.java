package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;

public class DepositMoneyController implements Initializable {
    // 1. KHAI BÁO FXML COMPONENTS
    // =========================================================================

    @FXML private TextField txtAmount;
    @FXML private Label lblAmountText;
    @FXML
    private Button btnCreateQr;

    // Cột bên phải — ẩn đi ban đầu
    @FXML private HBox infoPanel;        // HBox chứa toàn bộ phần thông tin chuyển khoản
    @FXML private ImageView imgQrCode;
    @FXML private Label lblTimer;
    @FXML private Label lblAccountNumber;
    @FXML private Label lblBankName;
    @FXML private Label lblAccountOwner;
    @FXML private Label lblContent;
    @FXML private Button btnConfirm;

    // =========================================================================
    // 2. CONSTANTS & STATE
    // =========================================================================

    private static final long MIN_DEPOSIT    = 10_000L;
    private static final long MAX_DEPOSIT    = 500_000_000L;
    private static final int  TIMER_SECONDS  = 15 * 60; // 15 phút

    // Cố định một ngân hàng duy nhất để tăng độ Trust
    private static final String COMPANY_BANK_NAME = "Techcombank (TCB)";
    private static final String COMPANY_ACCOUNT_NO = "1903 4567 8901 23";
    private static final String COMPANY_OWNER = "CONG TY TNHH PEAKBID";

    // Cờ chống vòng lặp vô hạn khi format tiền
    private boolean isFormattingAmount = false;

    // Timer đếm ngược
    private Timeline countdownTimer;
    private int remainingSeconds;

    // =========================================================================
    // 3. KHỞI TẠO (initialize)
    // =========================================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupAmountFormatter();
        hideInfoPanel();
        btnConfirm.setVisible(false);
        btnConfirm.setManaged(false);
    }

    // =========================================================================
    // 4. SETUP HELPERS
    // =========================================================================
    @FXML
    private void handleQuickAmount(ActionEvent event) {
        Button clickedButton = (Button) event.getSource();
        // Lấy Text trên nút (VD: "500.000đ") và chỉ lọc lấy số
        String rawAmount = clickedButton.getText().replaceAll("[^\\d]", "");
        txtAmount.setText(rawAmount);
    }
    /**
     * Lắng nghe thay đổi trên txtAmount:
     * - Lọc ký tự không phải số
     * - Format dấu chấm phân cách hàng nghìn
     * - Cập nhật chữ số bằng tiếng Việt
     * - Giới hạn MAX_DEPOSIT
     */
    private void setupAmountFormatter() {
        txtAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isFormattingAmount) return;

            // Chỉ giữ lại chữ số
            String rawDigits = newVal.replaceAll("[^\\d]", "");

            if (rawDigits.isEmpty()) {
                isFormattingAmount = true;
                txtAmount.setText("");
                lblAmountText.setText("Bằng chữ: Chưa xác định");
                isFormattingAmount = false;
                return;
            }

            // Chặn vượt 18 chữ số (an toàn với Long)
            if (rawDigits.length() > 18) {
                rawDigits = rawDigits.substring(0, 18);
            }

            try {
                isFormattingAmount = true;
                long amount = Long.parseLong(rawDigits);

                // Giới hạn MAX
                if (amount > MAX_DEPOSIT) {
                    amount = MAX_DEPOSIT;
                    rawDigits = String.valueOf(amount);
                }

                String formatted = CurrencyFormatter.formatNumber(amount);
                txtAmount.setText(formatted);
                lblAmountText.setText("Bằng chữ: " + CurrencyFormatter.numberToWords(amount) + " đồng");

                // Đẩy con trỏ về cuối sau khi format xong
                Platform.runLater(() -> txtAmount.positionCaret(txtAmount.getText().length()));

            } catch (NumberFormatException e) {
                txtAmount.setText(oldVal);
            } finally {
                isFormattingAmount = false;
            }
        });
    }

    // =========================================================================
    // 5. XỬ LÝ SỰ KIỆN — TẠO MÃ THANH TOÁN
    // =========================================================================

    @FXML
    private void handleCreateQr() {
        // --- Validate ---
        long amount = parsedAmount();
        if (amount < MIN_DEPOSIT) {
            AlertUtils.showWarning("Số tiền không hợp lệ",
                    "Vui lòng nhập số tiền tối thiểu " + CurrencyFormatter.formatVND(MIN_DEPOSIT) + ".");
            return;
        }
        // Điền thông tin tĩnh (Hardcoded)
        lblBankName.setText(COMPANY_BANK_NAME);
        lblAccountNumber.setText(COMPANY_ACCOUNT_NO);
        lblAccountOwner.setText(COMPANY_OWNER);
        lblContent.setText(generateTransferContent());

        // Hiện panel, MỞ KHÓA nút Xác nhận & khởi động timer
        showInfoPanel();
        btnConfirm.setVisible(true);
        btnConfirm.setManaged(true);
        startCountdown();
    }

    // =========================================================================
    // 6. XỬ LÝ SỰ KIỆN — XÁC NHẬN ĐÃ CHUYỂN KHOẢN
    // =========================================================================

    @FXML
    private void handleConfirmPayment(ActionEvent event) {
        stopCountdown();
        // Gửi yêu cầu lên Server
        sendDepositRequest();

        // Thông báo người dùng
        AlertUtils.showSuccess("Đang xử lý",
                "Hệ thống đang xử lý giao dịch. Tiền sẽ được cộng vào tài khoản sau ít phút.");

        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/ProfileContent.fxml");
    }

    // =========================================================================
    // 7. TIMER ĐẾM NGƯỢC
    // =========================================================================

    private void startCountdown() {
        stopCountdown(); // Dừng timer cũ nếu đang chạy

        remainingSeconds = TIMER_SECONDS;
        updateTimerLabel(remainingSeconds);

        countdownTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    remainingSeconds--;
                    updateTimerLabel(remainingSeconds);

                    if (remainingSeconds <= 0) {
                        onTimerExpired();
                    }
                })
        );
        countdownTimer.setCycleCount(TIMER_SECONDS);
        countdownTimer.play();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void updateTimerLabel(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        lblTimer.setText(String.format("Hiệu lực trong: %02d:%02d", minutes, seconds));
    }

    private void onTimerExpired() {
        stopCountdown();
        hideInfoPanel();
        AlertUtils.showWarning("Mã QR hết hạn", "Mã QR đã hết hạn, vui lòng tạo lại.");
    }

    // =========================================================================
    // 8. GỬI YÊU CẦU NẠP TIỀN LÊN SERVER
    // =========================================================================

    /**
     * Gửi Message loại "DEPOSIT_REQUEST" lên Server.
     * Payload: Object[] { userId, amount, transferContent, bankName }
     *
     * NOTE: Server cần xử lý case "DEPOSIT_REQUEST" trong ClientHandler.dispatch()
     * và ServerEventListener cần thêm: void onDepositResult(boolean isSuccess, String message);
     */
    private void sendDepositRequest() {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client == null) {
            System.err.println("DEPOSIT: Không có kết nối Socket.");
            return;
        }

        User currentUser = SessionManager.getInstance().getCurrentUser();
        String userId     = currentUser != null ? currentUser.getId() : "GUEST";
        long   amount     = parsedAmount();
        String content    = lblContent.getText();

        Object[] payload = { userId, amount, content, COMPANY_BANK_NAME };
        client.sendToServer(new Message("DEPOSIT_REQUEST", payload));
        System.out.println("DEPOSIT: Đã gửi yêu cầu nạp " + amount + " VNĐ cho user " + userId);
    }

    // =========================================================================
    // 9. UTILITY / HELPER METHODS
    // =========================================================================

    /**
     * Tạo nội dung chuyển khoản gắn với user hiện tại.
     * Ví dụ: "PKBD NAP user123 882931"
     */
    private String generateTransferContent() {
        User user = SessionManager.getInstance().getCurrentUser();
        String userTag = (user != null && user.getUserName() != null)
                ? user.getUserName().replaceAll("[^a-zA-Z0-9]", "").toUpperCase()
                : "GUEST";
        // Rút gọn userTag nếu quá dài
        if (userTag.length() > 8) userTag = userTag.substring(0, 8);
        int randomCode = 100_000 + new Random().nextInt(900_000);
        return "PKBD NAP " + userTag + " " + randomCode;
    }

    /**
     * Parse số tiền từ TextField (bỏ dấu chấm định dạng).
     * Trả về 0 nếu trường rỗng hoặc không hợp lệ.
     */
    private long parsedAmount() {
        try {
            String raw = txtAmount.getText().replaceAll("[^\\d]", "");
            return raw.isEmpty() ? 0L : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void showInfoPanel() {
        if (infoPanel != null) infoPanel.setVisible(true);
        if (infoPanel != null) infoPanel.setManaged(true);
    }

    private void hideInfoPanel() {
        if (infoPanel != null) infoPanel.setVisible(false);
        if (infoPanel != null) infoPanel.setManaged(false);
    }
}

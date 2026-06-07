package com.nhomX.example.controller.client;

import java.net.URL;
import java.util.ResourceBundle;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.User;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class WithdrawMoneyController implements Initializable, ServerEventListener  {

    @FXML
    private TextField txtWithdrawAmount;

    @FXML
    private Label lblWithdrawAmountText;

    @FXML
    private ComboBox<String> cbBank;

    @FXML
    private TextField txtBeneficiaryAccount;

    @FXML
    private Button btnCreateWithdrawRequest;

    private AuctionClient auctionClient;
    private ActionEvent lastWithdrawEvent;
    private boolean withdrawResultHandled = false;
    private static final long MIN_WITHDRAW = 50_000L;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupBankComboBox();
        setupAmountFormatter();

        auctionClient = SessionManager.getInstance().getAuctionClient();

        if (auctionClient != null) {
            auctionClient.addListener(this);
        }
    }

    private void setupBankComboBox() {
        if (cbBank != null) {
            cbBank.getItems().setAll(
                    "BIDV",
                    "VietcomBank",
                    "Agribank",
                    "ViettinBank"
            );
        }
    }

    private void setupAmountFormatter() {
        if (txtWithdrawAmount == null) {
            return;
        }

        txtWithdrawAmount.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isBlank()) {
                if (lblWithdrawAmountText != null) {
                    lblWithdrawAmountText.setText("Bằng chữ: Chưa xác định");
                }
                return;
            }

            String digitsOnly = newValue.replaceAll("[^\\d]", "");

            if (digitsOnly.isEmpty()) {
                txtWithdrawAmount.clear();
                return;
            }

            try {
                long amount = Long.parseLong(digitsOnly);
                String formatted = CurrencyFormatter.formatNumber(amount);

                if (!newValue.equals(formatted)) {
                    txtWithdrawAmount.setText(formatted);
                    txtWithdrawAmount.positionCaret(formatted.length());
                }

                if (lblWithdrawAmountText != null) {
                    lblWithdrawAmountText.setText(
                            "Số tiền rút: " + CurrencyFormatter.formatVND(amount)
                    );
                }

            } catch (NumberFormatException e) {
                txtWithdrawAmount.setText(oldValue);
            }
        });
    }

    @FXML
    private void handleQuickWithdrawAmount(ActionEvent event) {
        if (!(event.getSource() instanceof Button button)) {
            return;
        }

        String rawText = button.getText().replaceAll("[^\\d]", "");

        if (rawText.isEmpty()) {
            return;
        }

        txtWithdrawAmount.setText(CurrencyFormatter.formatNumber(Long.parseLong(rawText)));
    }

    @FXML
    private void handleCreateWithdrawRequest(ActionEvent event) {
        User currentUser = SessionManager.getInstance().getCurrentUser();

        if (currentUser == null) {
            AlertUtils.showError("Lỗi phiên đăng nhập",
                    "Bạn cần đăng nhập trước khi rút tiền.");
            return;
        }

        long amount;

        try {
            amount = parseMoney(txtWithdrawAmount.getText());
        } catch (NumberFormatException e) {
            AlertUtils.showWarning("Số tiền không hợp lệ",
                    "Vui lòng nhập số tiền cần rút.");
            return;
        }

        if (amount < MIN_WITHDRAW) {
            AlertUtils.showWarning("Số tiền không hợp lệ",
                    "Số tiền rút tối thiểu là " + CurrencyFormatter.formatVND(MIN_WITHDRAW) + ".");
            return;
        }

        String selectedBank = cbBank.getValue();

        if (selectedBank == null || selectedBank.isBlank()) {
            AlertUtils.showWarning("Thiếu ngân hàng",
                    "Vui lòng chọn ngân hàng thụ hưởng.");
            return;
        }

        String accountNumber = txtBeneficiaryAccount.getText();

        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            AlertUtils.showWarning("Thiếu số tài khoản",
                    "Vui lòng nhập số tài khoản thụ hưởng.");
            return;
        }

        if (amount > currentUser.getBalance()) {
            AlertUtils.showWarning("Số dư không đủ",
                    "Số dư hiện tại của bạn là "
                            + CurrencyFormatter.formatVND(currentUser.getBalance()) + ".");
            return;
        }

        if (auctionClient == null) {
            AlertUtils.showError("Lỗi kết nối",
                    "Không tìm thấy kết nối tới server.");
            return;
        }

        btnCreateWithdrawRequest.setDisable(true);
        lastWithdrawEvent = event;

        auctionClient.requestWithdraw(
                currentUser.getId(),
                amount,
                selectedBank,
                accountNumber.trim()
        );

        if (lblWithdrawAmountText != null) {
            lblWithdrawAmountText.setText("Đang gửi yêu cầu rút tiền lên hệ thống...");
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        if (auctionClient != null) {
            auctionClient.removeListener(this);
        }

        goBackToProfile();
    }

    private void goBackToProfile() {
        try {
            SceneSwitcher.switchSceneInline(
                    btnCreateWithdrawRequest,
                    "/com/nhomX/example/fxml/client/dashboard.fxml"
            );

            Platform.runLater(() -> {
                if (MainDashBoardController.instance != null) {
                    MainDashBoardController.instance.loadView(
                            "/com/nhomX/example/fxml/client/ProfileContent.fxml"
                    );
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            AlertUtils.showError("Lỗi", "Không thể quay lại trang hồ sơ.");
        }
    }

    private long parseMoney(String text) {
        if (text == null) {
            throw new NumberFormatException("empty money");
        }

        String cleaned = text.replaceAll("[^\\d]", "");

        if (cleaned.isEmpty()) {
            throw new NumberFormatException("empty money");
        }

        return Long.parseLong(cleaned);
    }
    @Override
    public void onWithdrawResult(boolean isSuccess, long newBalance, String message) {
        Platform.runLater(() -> {
            if (withdrawResultHandled) {
                return;
            }

            withdrawResultHandled = true;

            if (auctionClient != null) {
                auctionClient.removeListener(this);
            }

            if (btnCreateWithdrawRequest != null) {
                btnCreateWithdrawRequest.setDisable(false);
            }

            if (!isSuccess) {
                AlertUtils.showError("Rút tiền thất bại",
                        message == null ? "Không thể tạo lệnh rút tiền." : message);
                return;
            }

            User currentUser = SessionManager.getInstance().getCurrentUser();

            if (currentUser != null) {
                currentUser.setBalance(newBalance);
                SessionManager.getInstance().login(currentUser);
            }

            AlertUtils.showSuccess("Tạo lệnh rút tiền thành công",
                    message == null ? "Yêu cầu rút tiền đã được xử lý thành công." : message);

            goBackToProfile();
        });
    }
}
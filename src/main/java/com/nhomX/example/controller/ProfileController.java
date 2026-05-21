package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class ProfileController extends BaseController implements Initializable, ServerEventListener {
    // ==========================================
    // KHAI BÁO UI COMPONENTS
    // ==========================================
    @FXML private ImageView imgAvatar;
    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone; // LƯU Ý: Em nhớ sửa FXML từ txtUsername thành txtPhone nhé!

    @FXML private Button btnSave;
    @FXML private Button btnChangePassword;
    @FXML private Button btnDeposit;

    @FXML private Label lblBalance;
    @FXML private Label lblTotalBids;
    @FXML private Label lblAuctionsWon;
    @FXML private Label lblWinRate;
    @FXML private ProgressBar progressWinRate;
    @FXML private Label lblRepScore;
    @FXML private VBox activityList;

    private User currentUser;
    private AuctionClient auctionClient;
    private File selectedAvatarFile; // Lưu trữ file ảnh nếu người dùng chọn đổi Avatar

    // ==========================================
    // KHỞI TẠO DỮ LIỆU BAN ĐẦU
    // ==========================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentUser = SessionManager.getInstance().getCurrentUser();
        auctionClient = SessionManager.getInstance().getAuctionClient();

        if (auctionClient != null) {
            auctionClient.setServerEventListener(this);
        }

        if (currentUser != null) {
            loadUserDataToUI();
        } else {
            AlertUtils.showError("Lỗi phiên", "Không tìm thấy thông tin đăng nhập. Vui lòng đăng nhập lại!");
        }
    }

    private void loadUserDataToUI() {
        // 1. Load Thông tin cá nhân
        txtFullName.setText(currentUser.getFullName());
        txtEmail.setText(currentUser.getUserName()); // Hệ thống đang dùng Email làm Username
        // Nếu User model của em có trường Phone, hãy thay bằng getPhone()
        txtPhone.setText("0987654321"); // Mock data tạm thời

        // 2. Load Tài chính
        // Chú ý: Cần có phương thức getBalance() trong model User của em
        long balance = currentUser.getBalance();
        lblBalance.setText(CurrencyFormatter.formatVND(balance).replace(" VNĐ", ""));

        // 3. Load Thống kê (Mock data - Ở bản thực tế sẽ fetch từ Server)
        lblTotalBids.setText("128");
        lblAuctionsWon.setText("42");
        lblWinRate.setText("32.8%");
        progressWinRate.setProgress(0.328);
        lblRepScore.setText("9.8/10");

        // Load Avatar nếu có đường dẫn
        // String avatarPath = currentUser.getAvatar(); ... (Tùy thuộc vào model của em)
    }

    // ==========================================
    // XỬ LÝ CÁC SỰ KIỆN (FEATURES)
    // ==========================================

    @FXML
    private void handleChangeAvatar(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh đại diện mới");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        // Mở hộp thoại chọn file
        selectedAvatarFile = fileChooser.showOpenDialog(null);

        if (selectedAvatarFile != null) {
            // Hiển thị preview ngay lập tức (Chưa lưu lên server)
            Image newAvatar = new Image(selectedAvatarFile.toURI().toString());
            imgAvatar.setImage(newAvatar);
        }
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        String newFullName = txtFullName.getText().trim();
        String newPhone = txtPhone.getText().trim();

        if (newFullName.isEmpty()) {
            AlertUtils.showWarning("Thiếu thông tin", "Họ và tên không được để trống!");
            return;
        }

        btnSave.setDisable(true); // Chống click đúp (Double-click prevention)

        // Đóng gói dữ liệu gửi lên Server
        String[] profileData = {currentUser.getId(), newFullName, newPhone};
        if (auctionClient != null) {
            auctionClient.sendToServer(new Message("UPDATE_PROFILE", profileData));
            System.out.println("CLIENT: Gửi yêu cầu cập nhật Profile...");
        }
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        // Luồng đổi mật khẩu khá phức tạp, tốt nhất nên chuyển sang màn hình riêng
        // hoặc gọi một Custom Dialog có 3 ô: Mật khẩu cũ, Mới, Xác nhận
        System.out.println("Chuyển hướng đến màn hình đổi mật khẩu nội bộ.");
        // SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/ChangePassword.fxml");
    }

    @FXML
    private void handleDeposit(ActionEvent event) {
        // Sử dụng JavaFX TextInputDialog để nạp tiền nhanh
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nạp tiền vào ví");
        dialog.setHeaderText("Số dư hiện tại: " + lblBalance.getText() + " VNĐ");
        dialog.setContentText("Nhập số tiền muốn nạp (VNĐ):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(amountStr -> {
            try {
                long amount = Long.parseLong(amountStr.replace(".", "").replace(",", ""));
                if (amount < 10000) {
                    AlertUtils.showError("Lỗi", "Số tiền nạp tối thiểu là 10.000 VNĐ");
                    return;
                }

                // Gửi lệnh nạp tiền lên Server
                String[] depositData = {currentUser.getId(), String.valueOf(amount)};
                if (auctionClient != null) {
                    auctionClient.sendToServer(new Message("DEPOSIT_FUNDS", depositData));
                }

            } catch (NumberFormatException e) {
                AlertUtils.showError("Lỗi nhập liệu", "Vui lòng chỉ nhập số hợp lệ!");
            }
        });
    }

    @FXML
    private void handleViewAllActivity(ActionEvent event) {
        // Logic mở lịch sử giao dịch chi tiết
        System.out.println("Mở popup hoặc chuyển trang xem tất cả hoạt động...");
    }
    // LẮNG NGHE PHẢN HỒI TỪ SERVER

    // (Lưu ý: Em cần thêm các case xử lý UPDATE_PROFILE_SUCCESS và DEPOSIT_SUCCESS
    // trong hàm handleServerMessage của class AuctionClient để gọi về đây)

    // Hàm tự định nghĩa thêm để nhận kết quả từ Server
    public void onProfileUpdateResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            btnSave.setDisable(false);
            if (isSuccess) {
                AlertUtils.showSuccess("Thành công", "Cập nhật hồ sơ thành công!");
                // Cập nhật lại Model Nguồn
                currentUser.setFullName(txtFullName.getText().trim());
                SessionManager.getInstance().login(currentUser); // Ghi đè session
            } else {
                AlertUtils.showError("Thất bại", message);
            }
        });
    }

    public void onDepositResult(boolean isSuccess, long newBalance) {
        Platform.runLater(() -> {
            if (isSuccess) {
                AlertUtils.showSuccess("Thành công", "Nạp tiền thành công!");
                currentUser.setBalance(newBalance);
                lblBalance.setText(CurrencyFormatter.formatVND(newBalance).replace(" VNĐ", ""));
            } else {
                AlertUtils.showError("Thất bại", "Giao dịch nạp tiền thất bại, vui lòng thử lại.");
            }
        });
    }
}

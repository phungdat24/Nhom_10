package com.nhomX.example.controller.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SellerItemCardController {
    private static final Logger logger = LoggerFactory.getLogger(SellerItemCardController.class);
    // ==========================================
    // KHAI BÁO CÁC COMPONENT TỪ FXML
    // ==========================================
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblBidCount;
    @FXML
    private ImageView itemImageView;
    @FXML
    private Label lblItemName;
    @FXML
    private Label lblStartingPrice;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblTimeCaption;
    @FXML
    private Label lblTimeValue;
    @FXML
    private HBox actionBox;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;
    @FXML
    private Label lblTimeCaption1;
    @FXML
    private Label lblTimeValue1;
    // [THÊM MỚI] Biến Callback để bấm chuông gọi Controller Cha
    private Runnable onStatusChangeCallback;

    @FXML
    private void initialize() {
        btnEdit.setOnAction(this::handleEditAction);
        btnDelete.setOnAction(this::handleDeleteAction);
    }

    public void setOnStatusChangeCallback(Runnable callback) {
        this.onStatusChangeCallback = callback;
    }

    private Auction currentAuction;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    // Ổ khóa quản lý luồng đếm ngược của riêng thẻ này
    private Timeline countdownTimeline;

    // ==========================================
    // TÁC VỤ 1: ĐỔ DỮ LIỆU TỪ MODEL LÊN GIAO DIỆN
    // ==========================================
    public void setData(Auction auction) {
        this.currentAuction = auction;

        // 1. Gắn text cơ bản
        lblItemName.setText(auction.getItem().getTitle());
        lblStartingPrice.setText(CurrencyFormatter.formatVND(auction.getStartingPrice()));
        lblCurrentPrice.setText(CurrencyFormatter.formatVND(auction.getHighestBid()));

        // 2. Load Ảnh sản phẩm (Có cơ chế fallback ảnh mặc định)
        loadImage(auction.getItem().getImages());
        // [SỬA LỖI GÁN CỨNG]: Bơm thời gian Bắt đầu thực tế từ DB vào
        if (lblTimeValue1 != null) {
            lblTimeValue1.setText(
                    auction.getStartTime() != null ? auction.getStartTime().format(formatter)
                            : "Đang cập nhật");
        }

        // 3. Xử lý Trạng thái, Màu sắc, Thời gian và Khóa nút
        updateUIByStatus(auction.getStatus().name());
    }

    // ==========================================
    // TÁC VỤ 2: STATE MACHINE (QUẢN LÝ TRẠNG THÁI)
    // ==========================================
    private void updateUIByStatus(String statusStr) {
        // Mặc định ẩn số lượt Bid (Chỉ hiện khi đang RUNNING)
        lblBidCount.setVisible(false);
        // Xóa bộ đếm cũ nếu có để tránh trùng lặp luồng chạy ngầm (Memory Leak)
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        switch (statusStr) {
            case "PENDING":
                lblStatus.setText("CHỜ DUYỆT");
                lblStatus.setStyle(
                        "-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Tạo lúc:");
                // Nếu DB không có create_time, ta tạm để trống hoặc lấy thời gian hiện tại
                lblTimeValue.setText("Đợi Admin phê duyệt");
                enableActions(true); // Cho phép sửa/xóa
                break;
            case "UP_COMING": // [THÊM MỚI] SẮP LÊN SÀN
                lblStatus.setText("SẮP LÊN SÀN");
                lblStatus.setStyle(
                        "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Mở bán sau: ");
                lblBidCount.setVisible(false); // Chưa ai được đặt

                // Đếm ngược đến giờ BẮT ĐẦU (StartTime). Cờ isOpenCountdown = true
                startCountdown(currentAuction.getStartTime(), true);
                enableActions(true); // Vẫn cho phép người bán sửa/xóa vì chưa mở cửa
                break;

            case "OPEN":
                lblStatus.setText("ĐANG MỞ");
                lblStatus.setStyle(
                        "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Kết thúc: ");
                lblBidCount.setVisible(true);
                lblBidCount.setText("✨ Mới");

                startCountdown(currentAuction.getEndTime(), false); // Đếm ngược đến giờ chốt sổ
                enableActions(false);
                break;

            case "RUNNING":
                lblStatus.setText("ĐANG ĐẤU GIÁ");
                lblStatus.setStyle(
                        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Kết thúc:");
                lblTimeValue.setText(currentAuction.getEndTime() != null
                        ? currentAuction.getEndTime().format(formatter)
                        : "--");

                // Hiện số lượt Bid (Mock UI hoặc lấy thật nếu Model của em có hàm getBidCount)
                lblBidCount.setVisible(true);
                lblBidCount.setText("🔥 Đang hot");
                startCountdown(currentAuction.getEndTime(), false);
                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;

            case "FINISHED":
            case "PAID":
                lblStatus.setText("ĐÃ BÁN");
                lblStatus.setStyle(
                        "-fx-background-color: #c9a227; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Kết thúc lúc:");
                lblTimeValue.setText(currentAuction.getEndTime() != null
                        ? currentAuction.getEndTime().format(formatter)
                        : "--");
                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;

            case "CANCELED":
                lblStatus.setText("ĐÃ HỦY");
                lblStatus.setStyle(
                        "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Trạng thái:");
                lblTimeValue.setText("Bị vô hiệu hóa");
                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;
        }
    }

    // [THAY THẾ] HÀM ĐẾM NGƯỢC THÔNG MINH MỚI
    // ==========================================
    private void startCountdown(LocalDateTime targetTime, boolean isOpenCountdown) {
        if (targetTime == null) {
            lblTimeValue.setText("--:--:--");
            return;
        }

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            java.time.Duration duration =
                    java.time.Duration.between(LocalDateTime.now(), targetTime);

            if (duration.isNegative() || duration.isZero()) {
                countdownTimeline.stop(); // Dừng đồng hồ

                if (isOpenCountdown) {
                    // NẾU LÀ ĐẾM NGƯỢC MỞ BÁN -> Tới giờ thì Bấm chuông gọi trang Cha chuyển tab
                    currentAuction.setStatus(AuctionStatus.OPEN); // Đổi trạng thái nội bộ
                    if (onStatusChangeCallback != null) {
                        onStatusChangeCallback.run(); // Kích hoạt callback!
                    }
                } else {
                    // NẾU LÀ ĐẾM NGƯỢC KẾT THÚC -> Hiện chữ Hết giờ
                    lblTimeValue.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    lblTimeValue.setText("00:00:00 (Hết giờ)");
                }
            } else {
                long days = duration.toDays();
                long hours = duration.toHoursPart(); // [FIX BUG] Dùng Part để không cộng dồn ngày
                long minutes = duration.toMinutesPart();
                long seconds = duration.toSecondsPart();

                if (days > 0) {
                    lblTimeValue.setText(
                            String.format("%d ngày %02d:%02d:%02d", days, hours, minutes, seconds));
                    lblTimeValue.setStyle("-fx-text-fill: #2f3542;");
                } else {
                    lblTimeValue.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                    if (hours == 0) {
                        lblTimeValue.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
                    } else {
                        lblTimeValue.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    }
                }
            }
        }));

        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    /**
     * Khóa hoặc mở khóa khu vực nút bấm
     */
    private void enableActions(boolean isEnabled) {
        btnEdit.setDisable(!isEnabled);
        btnDelete.setDisable(!isEnabled);

        if (!isEnabled) {
            // Đổi màu xám để báo hiệu cho người dùng biết nút đã bị liệt
            btnEdit.setStyle(
                    "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
            btnDelete.setStyle(
                    "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    // ==========================================
    // TÁC VỤ 3: TẢI ẢNH AN TOÀN
    // ==========================================
    private void loadImage(List<ItemImage> images) {
        // [REFACTOR]: Xóa bỏ việc đọc file cục bộ, ủy quyền hoàn toàn cho ImageLoader gọi mạng
        if (images == null || images.isEmpty() || images.get(0).getImagePath() == null
                || images.get(0).getImagePath().trim().isEmpty()) {
            // Truyền null để ImageLoader tự động set ảnh mặc định (Placeholder)
            ImageLoader.loadAsync(null, itemImageView);
        } else {
            // Lấy đúng tên file (VD: item_123abc_0.jpg) gửi lên Server để xin ảnh
            String fileName = images.get(0).getImagePath().trim();
            ImageLoader.loadAsync(fileName, itemImageView);
        }
    }

    // ==========================================
    // TÁC VỤ 4: BẮT SỰ KIỆN NÚT BẤM
    // ==========================================
    @FXML
    private void handleEditAction(ActionEvent event) {
        if (currentAuction == null) {
            AlertUtils.showError("Loi", "Khong tim thay san pham can sua.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/nhomX/example/fxml/client/AddItemcard.fxml"));
            Parent root = loader.load();

            AddItemcardController controller = loader.getController();
            controller.initForEdit(currentAuction);

            Stage popupStage = new Stage();
            popupStage.setTitle("Sua san pham - " + currentAuction.getItem().getTitle());
            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);
            popupStage.initModality(Modality.WINDOW_MODAL);
            popupStage.initOwner(((Node) event.getSource()).getScene().getWindow());
            popupStage.showAndWait();

            if (onStatusChangeCallback != null) {
                onStatusChangeCallback.run();
            }
        } catch (Exception e) {
            logger.error("Loi khi mo popup sua san pham", e);
            AlertUtils.showError("Loi", "Khong the mo popup sua san pham.");
        }
    }

    @FXML
    private void handleDeleteAction(ActionEvent event) {
        // Yêu cầu xác nhận trước khi xóa (UX chống bấm nhầm)
        boolean confirm = AlertUtils.showConfirmation("Xác nhận xóa",
                "Bạn có chắc chắn muốn xóa sản phẩm '" + currentAuction.getItem().getTitle()
                        + "' không? Hành động này không thể hoàn tác.");

        if (confirm) {
            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client != null) {
                // Đóng gói ID sản phẩm để Server tìm và diệt
                client.sendToServer(
                        new Message("DELETE_PRODUCT", currentAuction.getItem().getId()));
                logger.info("CLIENT: Yêu cầu xóa sản phẩm {}", currentAuction.getItem().getId());
            }
        }
    }

}

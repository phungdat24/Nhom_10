package com.nhomX.example.controller.client;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

public class ItemCardController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(ItemCardController.class);
    @FXML
    // Thời gian
    private Label lblTimeLeft;
    // Gia hiện tại
    @FXML
    private Label lblCurrentPrice;
    // Tên vật phẩm
    @FXML
    private Label lblItemName;
    @FXML
    private Label lblStartingPrice;
    @FXML
    private Label lblStartTime;
    @FXML
    private Label lblEndTime;
    // Anhr mô tả
    @FXML
    private ImageView imgProduct;

    @FXML
    private ImageView itemImageView;
    private Timeline countdownTimer; // Bộ đếm ngược thời gian thực

    private Auction currentAuction;
    // Định dạng thời gian chuẩn để tái sử dụng
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    // Hàm này sẽ được MainDashboardController gọi để nhồi dữ liệu vào
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        Items item = auction.getItem();
        lblItemName.setText(item.getTitle());
        lblStartingPrice.setText(CurrencyFormatter.formatVND(auction.getStartingPrice()));
        lblCurrentPrice.setText(CurrencyFormatter.formatVND(auction.getHighestBid()));

        if (auction.getStartTime() != null) {
            lblStartTime.setText(auction.getStartTime().format(formatter));
        }
        if (auction.getEndTime() != null) {
            lblEndTime.setText(auction.getEndTime().format(formatter));
        }
        // Load ảnh bằng Utils
        List<ItemImage> images = item.getImages();
        if (images != null && !images.isEmpty() && images.get(0).getImagePath() != null) {
            String fileName = images.get(0).getImagePath().trim();
            ImageLoader.loadAsync(fileName, itemImageView);
        } else {
            ImageLoader.loadAsync(null, itemImageView);
        }

        // Tối ưu: Bỏ qua việc tính toán tĩnh, giao toàn quyền cho Timer đếm ngược xử lý UI
        startRealtimeCountdown();
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        // Khi người dùng bấm "Đấu giá" ở ĐÚNG ô sản phẩm này
        logger.info("Đang mở cửa sổ đấu giá cho: {}", currentAuction.getId());
        // Lấy ID sản phẩm: currentItem.getId() để gửi qua Socket cho Member 2
    }

    @FXML
    void handleDetailAction(ActionEvent event) {
        try {
            // 1. Tải giao diện trang Chi tiết
            FXMLLoader loader = new FXMLLoader(getClass()
                    .getResource("/com/nhomX/example/fxml/client/ItemDetailContent.fxml"));
            Parent root = loader.load();

            // 2. Lấy bộ điều khiển của trang Chi tiết và truyền dữ liệu sản phẩm qua
            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(this.currentAuction);

            // 3. THAY ĐỔI QUAN TRỌNG: Thay vì setScene đập đi xây lại,
            // ta nhờ "Quản gia" MainDashBoardController nhét cái giao diện này vào giữa màn hình
            if (MainDashBoardController.instance != null) {
                MainDashBoardController.instance.saveCurrentViewAndNavigate(root);
            } else {
                System.err.println("Lỗi: MainController chưa được khởi tạo!");
            }

        } catch (IOException e) {
            logger.error("Lỗi khi mở trang chi tiết sản phẩm", e);
            logger.error("KHÔNG THỂ MỞ TRANG CHI TIẾT SẢN PHẨM! {}", e.getMessage());
        }
        logger.info("Information: {}", currentAuction.getItem().getDescription());
    }

    public void updateRealtimePrice(long newPrice, LocalDateTime newEndTime) {
        Platform.runLater(() -> {
            // 1. Cập nhật Model (Nguồn sự thật)
            this.currentAuction.setHighestBid(newPrice);
            if (newEndTime != null) {
                this.currentAuction.setEndTime(newEndTime); // Cập nhật thời gian nếu có
                                                            // Anti-sniping
            }

            // 2. Cập nhật UI
            lblCurrentPrice.setText(CurrencyFormatter.formatVND(newPrice));

            // 3. Hiệu ứng nháy màu đỏ để thu hút sự chú ý
            lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

            // Tự động trả về màu cũ sau 1.5 giây
            Timeline colorReset =
                    new Timeline(new KeyFrame(javafx.util.Duration.seconds(1.5), e -> {
                        lblCurrentPrice.setStyle("-fx-text-fill: #2b1f12; -fx-font-weight: bold;"); // Thay
                                                                                                    // bằng
                                                                                                    // mã
                                                                                                    // màu
                                                                                                    // mặc
                                                                                                    // định
                                                                                                    // của
                                                                                                    // CSS
                                                                                                    // em
                    }));
            colorReset.play();
        });
    }

    // TASK 4: LOGIC ĐỒNG HỒ ĐẾM NGƯỢC (TỰ ĐỘNG CHẬP NHẬT ANTI-SNIPING)
    // ==========================================
    private void startRealtimeCountdown() {
        // Dọn dẹp timer cũ nếu thẻ này được tái sử dụng (chống tràn RAM)
        if (countdownTimer != null)
            countdownTimer.stop();

        countdownTimer = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            // Mỗi giây trôi qua, lôi bản ghi mới nhất từ RAM ra để "ghi đè" lên bản cũ
            Auction freshData =
                    AuctionManager.getInstance().getAuctionById(this.currentAuction.getId());
            if (freshData != null) {
                this.currentAuction = freshData; // Cập nhật tham chiếu

                // Đồng bộ Giá hiện tại (Nếu có người vừa đấu giá xong)
                lblCurrentPrice
                        .setText(CurrencyFormatter.formatVND(this.currentAuction.getHighestBid()));
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = currentAuction.getStartTime();

            // LUÔN LẤY END TIME MỚI NHẤT (Giải quyết bài toán Anti-Sniping)
            LocalDateTime end = currentAuction.getEndTime();

            if (start != null && now.isBefore(start)) {
                // Trạng thái 1: Chưa mở bán
                lblTimeLeft.setText(
                        "Sắp mở: " + start.format(DateTimeFormatter.ofPattern("HH:mm dd/MM")));
                lblTimeLeft.setStyle("-fx-text-fill: #d35400; -fx-font-weight: bold;");
            } else if (end != null && now.isBefore(end)) {
                // Trạng thái 2: Đang diễn ra -> Đếm ngược
                Duration duration = Duration.between(now, end);
                long days = duration.toDays();
                long hours = duration.toHoursPart();
                long minutes = duration.toMinutesPart();
                long seconds = duration.toSecondsPart();

                if (days > 0) {
                    lblTimeLeft.setText(String.format("Còn: %d ngày %02d:%02d:%02d", days, hours,
                            minutes, seconds));
                } else {
                    lblTimeLeft
                            .setText(String.format("Còn: %02d:%02d:%02d", hours, minutes, seconds));
                    // Đổi sang màu đỏ chớp tắt nếu dưới 5 phút (Tăng kịch tính)
                    if (hours == 0 && minutes < 5) {
                        lblTimeLeft.setStyle(
                                seconds % 2 == 0 ? "-fx-text-fill: #c0392b; -fx-font-weight: bold;"
                                        : "-fx-text-fill: #e74c3c;");
                    } else {
                        lblTimeLeft.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    }
                }

                // Cập nhật lại Label Thời gian kết thúc tĩnh (Phòng trường hợp Anti-sniping vừa
                // cộng giờ)
                lblEndTime.setText(end.format(formatter));

            } else {
                // Trạng thái 3: Đã kết thúc
                lblTimeLeft.setText("Đã kết thúc");
                lblTimeLeft.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
                if (countdownTimer != null)
                    countdownTimer.stop(); // Tiết kiệm CPU
            }
        }));

        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        countdownTimer.play();
    }

}

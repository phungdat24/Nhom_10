package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DashboardContentController extends BaseController implements ServerEventListener {
    @FXML private Label lblFeaturedName;
    @FXML private Label lblFeaturedPrice;
    @FXML private Label lblFeaturedTime;
    @FXML private ImageView imgFeatured;
    @FXML private Label lblOnlineUsers;
    /* Các Label thống kê trên cùng
     Số Đang diễn ra: */
    @FXML
    private Label lblActiveAuctions;
    // Số Sắp kết thúc
    @FXML
    private Label lblEndingSoon;
    // Số Người tham gia
    @FXML
    private Label lblTotalUsers;
    @FXML
    private FlowPane contentArea;
    private Timeline countdownTimeline;

    private Auction featuredAuction; // Đối tượng lưu trữ sản phẩm nổi bật đang hiển thị

    @FXML
    public void initialize() {
        // 1. Cập nhật thông tin Header (Số dư, tên user)
        updateHeaderUI();
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        // 2. Đăng ký lắng nghe sự kiện từ Server
        if (client != null) {
            client.setServerEventListener(this);
            client.sendToServer(new Message("GET_DASHBOARD_DATA", null));
        }else {
            System.err.println("Cảnh báo: Không tìm thấy kết nối mạng Socket!");
        }
    }

    private void loadFeaturedData() {
        if (featuredAuction == null)  return;
        lblFeaturedName.setText(featuredAuction.getItem().getTitle());
        lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(featuredAuction.getHighestBid()));
            // 2. Load Hình ảnh (Giả định entity Items của em có hàm getImagePath() lưu đường dẫn ảnh)
            // Nếu em chưa có, tạm thời để comment đoạn này lại nhé.
        try {
            List<ItemImage> images = featuredAuction.getItem().getImages();
            if (images != null && !images.isEmpty()) {
                String imagePath = images.get(0).getImagePath();
                if (imagePath != null && !imagePath.isEmpty()) {
                    // Nạp đường dẫn String vào JavaFX Image
                    javafx.scene.image.Image img = new javafx.scene.image.Image(getClass().getResourceAsStream(imagePath));
                    imgFeatured.setImage(img);
                } else {
                    // (Tùy chọn) Nếu item không có ảnh nào, set một ảnh mặc định (Placeholder)
                    imgFeatured.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/images/default_item.png")));
                }
            }
        } catch (Exception e) {
                System.err.println("Không load được ảnh sản phẩm nổi bật: " + e.getMessage());
        }

            // 3. Logic Đếm ngược thời gian (Đồng hồ cát)
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Dừng đồng hồ cũ nếu có
        }

        countdownTimeline = new javafx.animation.Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = featuredAuction.getEndTime();

            if (now.isAfter(endTime)) {
                lblFeaturedTime.setText("⏱ Đã kết thúc!");
                countdownTimeline.stop();
            } else {
                java.time.Duration duration = java.time.Duration.between(now, endTime);
                long days = duration.toDays();
                long hours = duration.toHoursPart();
                long minutes = duration.toMinutesPart();
                long seconds = duration.toSecondsPart();

                String timeLeft = String.format("⏱ Còn lại: %d ngày %02d:%02d:%02d", days, hours, minutes, seconds);
                lblFeaturedTime.setText(timeLeft);
            }
        }));
            countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE); // Chạy vô hạn
            countdownTimeline.play();
    }


    /**
     * Xử lý khi nhấn nút "Đấu giá ngay" hoặc "Xem chi tiết"
     */
    @FXML
    void handleFeaturedBid(ActionEvent event) {
        navigateToDetail();
    }

    @FXML
    void handleFeaturedDetail(ActionEvent event) {
        navigateToDetail();
    }

    /**
     * Logic chuyển hướng sang trang chi tiết sản phẩm
     */
    private void navigateToDetail() {
        if (featuredAuction == null) return;

        try {
            // 1. Nạp giao diện chi tiết (ItemDetailContent.fxml)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemDetailContent.fxml"));
            Parent root = loader.load();

            // 2. Lấy Controller của trang chi tiết và truyền dữ liệu sản phẩm qua
            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(featuredAuction);

            // 3. Gọi "Quản gia" MainDashBoardController để thay đổi nội dung trung tâm
            if (MainDashBoardController.instance != null) {
                MainDashBoardController.instance.setCenterContent(root);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi chuyển hướng trang chi tiết: " + e.getMessage());
        }
    }

    // ===== LẮNG NGHE REAL-TIME CHO SẢN PHẨM NỔI BẬT =====
    @Override
    public void onHighestBidUpdated(String itemId, long newPrice, String bidderName) {
        // Nếu món hàng vừa nhảy giá chính là món đang hiện ở Dashboard, ta cập nhật ngay
        if (featuredAuction != null && featuredAuction.getId().equals(itemId)) {
            Platform.runLater(() -> {
                featuredAuction.setHighestBid(newPrice);
                lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(newPrice));
                System.out.println("Dashboard: Đã cập nhật giá mới cho sản phẩm Hero!");
            });
        }
    }
    @Override
    public void onDashboardDataReceived(Map<String, Integer> stats, List<Auction> endingSoon, List<Auction> trending) {
        Platform.runLater(() -> {
            // Kiểm tra an toàn trước khi set dữ liệu
            if (stats != null) {
                lblActiveAuctions.setText(String.valueOf(stats.getOrDefault("active", 0)));
                lblEndingSoon.setText(String.valueOf(stats.getOrDefault("ending", 0)));
                // [CẬP NHẬT] Lấy cả tổng số user và số online để nối chuỗi
                int totalUsers = stats.getOrDefault("users", 0);
                int onlineUsers = stats.getOrDefault("online", 0);

                // Set text đúng chuẩn format:
                lblTotalUsers.setText(String.valueOf(totalUsers));
                lblOnlineUsers.setText("(" + onlineUsers + " đang online)");
            }
            if (trending != null && !trending.isEmpty()) {
                this.featuredAuction = trending.get(0);
                // Gọi hàm đổ dữ liệu lên UI tại đây!
                loadFeaturedData();
            }
            // 3. [THÊM MỚI] Đổ dữ liệu các sản phẩm còn lại vào vùng trống bên dưới
            if (contentArea != null && trending != null) {
                contentArea.getChildren().clear(); // Dọn dẹp đồ cũ trước khi bày đồ mới

                // Bắt đầu vòng lặp từ i = 1 (Vì phần tử số 0 đã lấy làm Hero Banner ở trên rồi)
                for (int i = 1; i < trending.size(); i++) {
                    Auction auction = trending.get(i);
                    try {
                        // Tải bản thiết kế của 1 cái thẻ sản phẩm (Card)
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemCard.fxml"));
                        Node cardNode = loader.load();

                        // Truyền dữ liệu (Tên, giá, ảnh) vào cái thẻ đó
                        ItemCardController cardController = loader.getController();
                        //Gọi hàm setAuctionData bên class ItemCardController
                        cardController.setAuctionData(auction);

                        // Nhét thẻ đã có dữ liệu vào vùng chứa trên màn hình
                        contentArea.getChildren().add(cardNode);

                    } catch (Exception e) {
                        System.err.println("❌ Lỗi khi render ItemCard: " + e.getMessage());
                    }
                }
            }
        });
    }
    // ===== LẮNG NGHE REAL-TIME SỐ NGƯỜI ONLINE =====
    @Override
    public void onOnlineCountUpdated(int onlineCount) {
        Platform.runLater(() -> {
            // Nhớ kiểm tra null đề phòng UI chưa kịp render
            if (lblOnlineUsers != null) {
                lblOnlineUsers.setText("(" + onlineCount + " đang online)");
            }
        });
    }
}

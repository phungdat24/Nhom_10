package com.nhomX.example.controller.client;

import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;
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
    @FXML
    private Label lblFeaturedName;
    @FXML
    private Label lblFeaturedPrice;
    @FXML
    private Label lblFeaturedTime;
    @FXML
    private ImageView imgFeatured;
    @FXML
    private Label lblOnlineUsers;
    @FXML
    private Label lblActiveAuctions;
    @FXML
    private Label lblEndingSoon;
    @FXML
    private Label lblTotalUsers;
    @FXML
    private FlowPane contentArea;

    private Timeline countdownTimeline;

    // Chỉ lưu ID của sản phẩm nổi bật thay vì lưu nguyên Object để tránh dữ liệu bị cũ
    private String featuredAuctionId;

    @FXML
    public void initialize() {
        updateHeaderUI();
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);
            // Yêu cầu dữ liệu thống kê từ Server
            client.sendToServer(new Message("GET_DASHBOARD_DATA", null));
        } else {
            System.err.println("Cảnh báo: Không tìm thấy kết nối mạng Socket!");
        }
    }

    private void loadFeaturedData() {
        if (featuredAuctionId == null) return;

        // [REFACTOR 1]: Lấy dữ liệu CHUẨN từ Single Source of Truth (AuctionManager)
        Auction freshAuction = AuctionManager.getInstance().getAuctionById(featuredAuctionId);
        if (freshAuction == null) return;

        lblFeaturedName.setText(freshAuction.getItem().getTitle());
        lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(freshAuction.getHighestBid()));

        try {
            List<ItemImage> images = freshAuction.getItem().getImages();
            if (images != null && !images.isEmpty()) {
                String fileName = images.get(0).getImagePath();
                ImageLoader.loadAsync(fileName, imgFeatured);
            }else {
                // Không có ảnh -> Ném null để ImageLoader tự hiện Placeholder
               ImageLoader.loadAsync(null, imgFeatured);
            }
        } catch (Exception e) {
            System.err.println("Không load được ảnh sản phẩm nổi bật: " + e.getMessage());
        }

        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            Auction latestHeroData = AuctionManager.getInstance().getAuctionById(featuredAuctionId);
            if (latestHeroData != null) {
                // Tự động đè giá mới nhất lên UI mỗi giây, bất chấp việc bị cướp Listener
                lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(latestHeroData.getHighestBid()));
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = freshAuction.getEndTime();

            if (now.isAfter(endTime)) {
                lblFeaturedTime.setText("⏱ Đã kết thúc!");
                countdownTimeline.stop();
                // Khi phát hiện món Hero đã hết giờ
                Platform.runLater(() -> {
                    // 1. Tạm thời đổi màu chữ để cảnh báo
                    lblFeaturedTime.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                    // 2. Tự động gửi lệnh xin Server một danh sách Hero mới để thay thế
                    AuctionClient client = SessionManager.getInstance().getAuctionClient();
                    if (client != null) {
                        System.out.println("CLIENT: Món Hero đã hết hạn, đang xin Server dữ liệu Dashboard mới...");
                        client.sendToServer(new Message("GET_DASHBOARD_DATA", null));
                    }
                });
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
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    @FXML
    void handleFeaturedBid(ActionEvent event) {
        navigateToDetail();
    }

    @FXML
    void handleFeaturedDetail(ActionEvent event) {
        navigateToDetail();
    }

    private void navigateToDetail() {
        if (featuredAuctionId == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/client/ItemDetailContent.fxml"));
            Parent root = loader.load();

            ItemDetailController detailController = loader.getController();

            // [REFACTOR 2]: Truyền đối tượng tươi mới nhất từ Cache sang trang chi tiết
            Auction freshAuction = AuctionManager.getInstance().getAuctionById(featuredAuctionId);
            detailController.setAuctionData(freshAuction);

            if (MainDashBoardController.instance != null) {
                MainDashBoardController.instance.saveCurrentViewAndNavigate(root);
            }
        } catch (IOException e) {
            System.err.println("Lỗi chuyển hướng trang chi tiết: " + e.getMessage());
        }
    }

    // ===== LẮNG NGHE REAL-TIME ĐỒNG BỘ QUA MANAGER =====
    @Override
    public void onHighestBidUpdated(String itemId, long newPrice, String bidderName) {
        // AuctionManager (ở AuctionClient) đã tự cập nhật dữ liệu.
        // Dashboard chỉ việc kiểm tra xem có phải món mình đang khoe không, nếu phải thì vẽ lại UI.
        if (featuredAuctionId != null && featuredAuctionId.equals(itemId)) {
            Platform.runLater(() -> {
                // Kéo giá mới từ Manager (đã được cập nhật)
                Auction freshAuction = AuctionManager.getInstance().getAuctionById(featuredAuctionId);
                if (freshAuction != null) {
                    lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(freshAuction.getHighestBid()));
                    System.out.println("Dashboard: Đã đồng bộ giá mới từ AuctionManager cho sản phẩm Hero!");
                }
            });
        }
    }

    @Override
    public void onDashboardDataReceived(Map<String, Integer> stats, List<Auction> endingSoon, List<Auction> trending) {
        Platform.runLater(() -> {
            if (stats != null) {
                lblActiveAuctions.setText(String.valueOf(stats.getOrDefault("active", 0)));
                lblEndingSoon.setText(String.valueOf(stats.getOrDefault("ending", 0)));

                int totalUsers = stats.getOrDefault("users", 0);
                int onlineUsers = stats.getOrDefault("online", 0);
                lblTotalUsers.setText(String.valueOf(totalUsers));
                lblOnlineUsers.setText("(" + onlineUsers + " đang online)");
            }

            if (trending != null && !trending.isEmpty()) {
                // [REFACTOR 3]: Nạp trending vào Cache của Manager để nó quản lý tập trung
                AuctionManager.getInstance().setAllAuctions(trending);

                // Lưu ID sản phẩm top 1 thay vì lưu Object
                this.featuredAuctionId = trending.get(0).getId();
                loadFeaturedData();

                // Đổ các sản phẩm còn lại vào vùng trống bên dưới
                if (contentArea != null && trending.size() > 1) { // [FIX BUG 3]: Kiểm tra size an toàn
                    contentArea.getChildren().clear();

                    for (int i = 1; i < trending.size(); i++) {
                        Auction auction = trending.get(i);
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/client/Itemcard.fxml"));
                            Node cardNode = loader.load();

                            ItemCardController cardController = loader.getController();
                            // Truyền ID hoặc Object từ Manager qua
                            cardController.setAuctionData(AuctionManager.getInstance().getAuctionById(auction.getId()));

                            contentArea.getChildren().add(cardNode);
                        } catch (Exception e) {
                            System.err.println("❌ Lỗi khi render ItemCard: " + e.getMessage());
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onOnlineCountUpdated(int onlineCount) {
        Platform.runLater(() -> {
            if (lblOnlineUsers != null) {
                lblOnlineUsers.setText("(" + onlineCount + " đang online)");
            }
        });
    }
}

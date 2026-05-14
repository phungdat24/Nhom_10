package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class DashboardContentController extends BaseController implements ServerEventListener {
    @FXML private Label lblFeaturedName;
    @FXML private Label lblFeaturedPrice;
    @FXML private Label lblFeaturedTime;
    @FXML private ImageView imgFeatured;

    private Auction featuredAuction; // Đối tượng lưu trữ sản phẩm nổi bật đang hiển thị

    @FXML
    public void initialize() {
        // 1. Cập nhật thông tin Header (Số dư, tên user)
        updateHeaderUI();

        // 2. Đăng ký lắng nghe sự kiện từ Server
        if (SessionManager.getInstance().getAuctionClient() != null) {
            SessionManager.getInstance().getAuctionClient().setServerEventListener(this);
        }

        // 3. [Logic đề xuất]: Lấy sản phẩm nổi bật
        // Trong thực tế, em sẽ gọi: this.featuredAuction = AuctionRepository.getHeroAuction();
        // Ở đây thầy giả định em đã có hàm lấy dữ liệu để đổ lên UI
        loadFeaturedData();
    }

    private void loadFeaturedData() {
        if (featuredAuction != null) {
            lblFeaturedName.setText(featuredAuction.getItem().getTitle());
            lblFeaturedPrice.setText("Giá hiện tại: " + CurrencyFormatter.formatVND(featuredAuction.getHighestBid()));
            // lblFeaturedTime.setText("..."); // Tính toán thời gian còn lại
        }
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
}

package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class SellerItemCardController {
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

    private Auction currentAuction;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

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

        // 3. Xử lý Trạng thái, Màu sắc, Thời gian và Khóa nút
        updateUIByStatus(auction.getStatus().name());
    }

    // ==========================================
    // TÁC VỤ 2: STATE MACHINE (QUẢN LÝ TRẠNG THÁI)
    // ==========================================
    private void updateUIByStatus(String statusStr) {
        // Mặc định ẩn số lượt Bid (Chỉ hiện khi đang RUNNING)
        lblBidCount.setVisible(false);

        switch (statusStr) {
            case "PENDING":
                lblStatus.setText("CHỜ DUYỆT");
                lblStatus.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Tạo lúc:");
                // Nếu DB không có create_time, ta tạm để trống hoặc lấy thời gian hiện tại
                lblTimeValue.setText("--/--/----");
                enableActions(true); // Cho phép sửa/xóa
                break;

            case "OPEN":
                lblStatus.setText("SẮP LÊN SÀN");
                lblStatus.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Bắt đầu:");
                lblTimeValue.setText(currentAuction.getStartTime() != null ? currentAuction.getStartTime().format(formatter) : "--");
                enableActions(true); // Vẫn cho phép sửa/xóa vì chưa ai đặt tiền
                break;

            case "RUNNING":
                lblStatus.setText("ĐANG ĐẤU GIÁ");
                lblStatus.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Kết thúc:");
                lblTimeValue.setText(currentAuction.getEndTime() != null ? currentAuction.getEndTime().format(formatter) : "--");

                // Hiện số lượt Bid (Mock UI hoặc lấy thật nếu Model của em có hàm getBidCount)
                lblBidCount.setVisible(true);
                lblBidCount.setText(currentAuction.getHighestBid() > currentAuction.getStartingPrice() ? "🔥 Đang hot" : "✨ Mới");

                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;

            case "FINISHED":
            case "PAID":
                lblStatus.setText("ĐÃ BÁN");
                lblStatus.setStyle("-fx-background-color: #c9a227; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Kết thúc lúc:");
                lblTimeValue.setText(currentAuction.getEndTime() != null ? currentAuction.getEndTime().format(formatter) : "--");
                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;

            case "CANCELED":
                lblStatus.setText("ĐÃ HỦY");
                lblStatus.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                lblTimeCaption.setText("Trạng thái:");
                lblTimeValue.setText("Bị vô hiệu hóa");
                enableActions(false); // [GUARD CLAUSE]: CẤM SỬA/XÓA
                break;
        }
    }

    /**
     * Khóa hoặc mở khóa khu vực nút bấm
     */
    private void enableActions(boolean isEnabled) {
        btnEdit.setDisable(!isEnabled);
        btnDelete.setDisable(!isEnabled);

        if (!isEnabled) {
            // Đổi màu xám để báo hiệu cho người dùng biết nút đã bị liệt
            btnEdit.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
            btnDelete.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    // ==========================================
    // TÁC VỤ 3: TẢI ẢNH AN TOÀN
    // ==========================================
    private void loadImage(List<ItemImage> images) {
        String basePath = "/com/nhomX/example/images/";
        String defaultImage = "default_item.png";
        try {
            if (images == null || images.isEmpty() || images.get(0).getImagePath() == null || images.get(0).getImagePath().trim().isEmpty()) {
                itemImageView.setImage(new Image(getClass().getResourceAsStream(basePath + defaultImage)));
            } else {
                String fileName = images.get(0).getImagePath().trim();
                var imageStream = getClass().getResourceAsStream(basePath + fileName);
                if (imageStream != null) {
                    itemImageView.setImage(new Image(imageStream));
                } else {
                    itemImageView.setImage(new Image(getClass().getResourceAsStream(basePath + defaultImage)));
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi load ảnh Seller Card: " + e.getMessage());
        }
    }

    // ==========================================
    // TÁC VỤ 4: BẮT SỰ KIỆN NÚT BẤM
    // ==========================================
    @FXML
    private void handleEditAction(ActionEvent event) {
        System.out.println("Sửa sản phẩm: " + currentAuction.getId());
        // Gọi SceneSwitcher chuyển sang form cập nhật sản phẩm
    }

    @FXML
    private void handleDeleteAction(ActionEvent event) {
        // Yêu cầu xác nhận trước khi xóa (UX chống bấm nhầm)
        boolean confirm = AlertUtils.showConfirmation("Xác nhận xóa",
                "Bạn có chắc chắn muốn xóa sản phẩm '" + currentAuction.getItem().getTitle() + "' không? Hành động này không thể hoàn tác.");

        if (confirm) {
            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client != null) {
                // Đóng gói ID sản phẩm để Server tìm và diệt
                client.sendToServer(new Message("DELETE_PRODUCT", currentAuction.getItem().getId()));
                System.out.println("CLIENT: Yêu cầu xóa sản phẩm " + currentAuction.getItem().getId());
            }
        }
    }
    
}

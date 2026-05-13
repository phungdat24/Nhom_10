package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class ItemCardController extends BaseController {
    @FXML
    // Thời gian
    private Label lblTimeLeft;
    // Gia hiện tại
    @FXML
    private Label lblCurrentPrice;
    // Tên vật phẩm
    @FXML
    private Label lblItemName;
    // Anhr mô tả
    @FXML
    private ImageView imgProduct;

    @FXML
    private ImageView itemImageView;


    private Auction currentAuction;

    // Hàm này sẽ được MainDashboardController gọi để nhồi dữ liệu vào
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        Items item = auction.getItem();
        lblItemName.setText(item.getTitle());
        lblCurrentPrice.setText("Giá: " + auction.getHighestBid() + " VNĐ");

        String basePath = "/com/nhomX/example/images/";
        List<ItemImage> images = item.getImages();

        try {
            if (images == null || images.isEmpty() || images.get(0).getImagePath() == null || images.get(0).getImagePath().trim().isEmpty()) {
                // Không có ảnh -> Load ảnh mặc định
                itemImageView.setImage(new Image(getClass().getResourceAsStream(basePath + "no_image.png")));
            } else {
                // Có ảnh -> Ghép thư mục gốc với tên file từ DB (VD: /.../images/dell_front.png)
                String fileName = images.get(0).getImagePath().trim();
                Image img = new Image(getClass().getResourceAsStream(basePath + fileName));

                // Nếu file bị lỗi (VD: đuôi png sai, file bị xóa mất) -> Load ảnh mặc định
                if (img.isError()) {
                    System.err.println("❌ Không thể đọc được file ảnh: " + fileName);
                    itemImageView.setImage(new Image(getClass().getResourceAsStream(basePath + "no_image.png")));
                } else {
                    itemImageView.setImage(img);
                }
            }
        } catch (NullPointerException e) {
            // Lỗi này xảy ra khi chính cái file "no_image.png" hoặc file thật KHÔNG TỒN TẠI trong thư mục resources
            System.err.println("❌ CẢNH BÁO: Thiếu file ảnh trong thư mục resources!");
        }

        // TODO: Logic set ảnh dựa theo item.getImagePath()
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = auction.getEndTime();
        LocalDateTime start = auction.getStartTime();
        if (start != null && now.isBefore(start)) {
            // Trạng thái 1: Chưa đến giờ mở bán
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM");
            lblTimeLeft.setText("Sắp mở lúc: " + start.format(formatter));
            lblTimeLeft.setStyle("-fx-text-fill: #d35400;"); // Chữ màu cam

        } else if (end != null && now.isBefore(end)) {
            // Trạng thái 2: Đang diễn ra -> Đếm ngược thời gian kết thúc
            Duration duration = Duration.between(now, end);
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();

            if (days > 0) {
                lblTimeLeft.setText(String.format("Còn lại: %d ngày %d giờ", days, hours));
            } else {
                lblTimeLeft.setText(String.format("Còn lại: %d giờ %d phút", hours, minutes));
            }
            lblTimeLeft.setStyle("-fx-text-fill: #27ae60;"); // Chữ màu xanh lá

        } else {
            // Trạng thái 3: Quá hạn end_time
            lblTimeLeft.setText("Đã kết thúc");
            lblTimeLeft.setStyle("-fx-text-fill: #c0392b;"); // Chữ màu đỏ
        }
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        // Khi người dùng bấm "Đấu giá" ở ĐÚNG ô sản phẩm này
        System.out.println("Đang mở cửa sổ đấu giá cho: " + currentAuction.getId());
        // Lấy ID sản phẩm: currentItem.getId() để gửi qua Socket cho Member 2
    }

    @FXML
    void handleDetailAction(ActionEvent event) {
        try {
            // 1. Tải giao diện trang Chi tiết
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/nhomX/example/fxml/ItemDetailContent.fxml"));
            Parent root = loader.load();

            // 2. Lấy bộ điều khiển của trang Chi tiết và truyền dữ liệu sản phẩm qua
            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(this.currentAuction);

            // 3. THAY ĐỔI QUAN TRỌNG: Thay vì setScene đập đi xây lại,
            // ta nhờ "Quản gia" MainController nhét cái giao diện này vào giữa màn hình
            if (MainController.instance != null) {
                MainController.instance.setCenterContent(root);
            } else {
                System.err.println("Lỗi: MainController chưa được khởi tạo!");
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("KHÔNG THỂ MỞ TRANG CHI TIẾT SẢN PHẨM! " + e.getMessage());
        }
        System.out.println("Information: " + currentAuction.getItem().getDescription());
    }

    public void updateRealtimePrice(long newPrice) {
        if (currentAuction != null) {
            currentAuction.setHighestBid(newPrice);

            // Dùng hàm format tiền tệ mà bạn đã định nghĩa ở các file khác
            String formattedPrice = com.nhomX.example.utils.CurrencyFormatter.formatVND(newPrice);
            lblCurrentPrice.setText("Giá: " + formattedPrice);

            // Tùy chọn: Thêm hiệu ứng nháy màu đỏ/vàng cho Label giá để người xem chú ý
            lblCurrentPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
    }
}

package com.nhomX.example.controller;

import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.MyAuctionStatus;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MyAuctionCardController {
    @FXML
    private Label lblStatusBadge;
    @FXML
    private ImageView itemImageView;
    @FXML
    private Label lblItemName;
    @FXML
    private Label lblMyBid;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblEndTime;
    @FXML
    private Label lblTimeLeft;
    @FXML
    private Button btnAction;
    @FXML
    private Button btnDetail;

    private MyAuctionDTO currentDTO;
    private Timeline cardTimeline;

    // Hàm này sẽ được MyAuctionsController gọi để nhồi dữ liệu vào thẻ
    public void setData(MyAuctionDTO dto) {
        this.currentDTO = dto;

        // 1. Gắn dữ liệu cơ bản
        lblItemName.setText(dto.getAuction().getItem().getTitle());
        lblMyBid.setText(CurrencyFormatter.formatVND(dto.getMyHighestBid()));
        lblCurrentPrice.setText(CurrencyFormatter.formatVND(dto.getAuction().getHighestBid()));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        lblEndTime.setText(dto.getAuction().getEndTime().format(formatter));

        // 2. Logic xử lý UX (Đổi màu và nút bấm theo trạng thái)
        updateStatusUI(dto.getMyStatus());
        startCardCountdown(dto.getAuction().getEndTime());

        // (Gợi ý: Em có thể copy đoạn code Timeline đếm ngược từ Dashboard thả vào đây cho lblTimeLeft)
    }
    private void startCardCountdown(LocalDateTime endTime) {
        if (cardTimeline != null) {
            cardTimeline.stop(); // Khử trùng luồng chạy ngầm chống giật lag UI
        }

        if (endTime == null || currentDTO.getMyStatus() == MyAuctionStatus.WON || currentDTO.getMyStatus() == MyAuctionStatus.LOST) {
            if (lblTimeLeft != null) lblTimeLeft.setText("Đã đóng phiên");
            return;
        }

        cardTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            java.time.Duration duration = java.time.Duration.between(LocalDateTime.now(), endTime);

            if (duration.isNegative() || duration.isZero()) {
                if (lblTimeLeft != null) lblTimeLeft.setText("Hết giờ");
                cardTimeline.stop();
            } else {
                long days = duration.toDays();
                long hours = duration.toHoursPart();
                long minutes = duration.toMinutesPart();
                long seconds = duration.toSecondsPart();

                if (lblTimeLeft != null) {
                    if (days > 0) {
                        // Hiển thị định dạng kèm số ngày: "X ngày HH:mm:ss"
                        lblTimeLeft.setText(String.format("⏳ Còn: %d ngày %02d:%02d:%02d", days, hours, minutes, seconds));
                    } else {
                        // Dưới 24 tiếng: hiển thị "HH:mm:ss" rực lửa
                        lblTimeLeft.setText(String.format("⏳ Còn: %02d:%02d:%02d", hours, minutes, seconds));
                    }
                }
            }
        }));

        cardTimeline.setCycleCount(Animation.INDEFINITE);
        cardTimeline.play();
    }

    private void updateStatusUI(MyAuctionStatus status) {
        // Dọn dẹp style cũ
        lblStatusBadge.getStyleClass().removeAll("badge-leading", "badge-outbid", "badge-won", "badge-lost");
        btnAction.getStyleClass().removeAll("btn-action-outbid", "btn-action-leading");

        switch (status) {
            case LEADING:
                lblStatusBadge.setText("👑 ĐANG DẪN ĐẦU");
                lblStatusBadge.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                btnAction.setText("Tăng giá thêm");
                btnAction.setDisable(false);
                break;
            case OUTBID:
                lblStatusBadge.setText("‼ BỊ VƯỢT GIÁ");
                lblStatusBadge.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                btnAction.setText("Bid lại ngay!");
                btnAction.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;"); // Nút đỏ hối thúc
                break;
            case WON:
                lblStatusBadge.setText("🏆 ĐÃ TRÚNG THẦU");
                lblStatusBadge.setStyle("-fx-background-color: #c9a227; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                btnAction.setText("Thanh toán");
                break;
            case LOST:
                lblStatusBadge.setText("❌ ĐÃ THUA");
                lblStatusBadge.setStyle("-fx-background-color: #757575; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4;");
                btnAction.setText("Xem kết quả");
                break;
        }
    }

    @FXML
    void handleAction(ActionEvent event) {
        // Bấm nút Bid thì chuyển sang trang Detail để họ nhập tiền
        handleDetailAction(event);
    }

    @FXML
    void handleDetailAction(ActionEvent event) {
        // Kiểm tra an toàn: Đảm bảo thẻ đã được nạp dữ liệu
        if (currentDTO == null || currentDTO.getAuction() == null) return;

        try {
            // 1. Nạp file giao diện chi tiết sản phẩm (ItemDetailContent.fxml)
            FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemDetailContent.fxml"));
            Parent root = loader.load();

            // 2. Lấy Controller của trang chi tiết và truyền gói dữ liệu Auction sang
            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(currentDTO.getAuction());

            // 3. Gọi "Quản gia" MainDashBoardController để đổi khung hình trung tâm
            if (MainDashBoardController.instance != null) {
                MainDashBoardController.instance.setCenterContent(root);
            }

        } catch (java.io.IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi chuyển hướng trang chi tiết từ thẻ MyAuction: " + e.getMessage());
        }
    }
}

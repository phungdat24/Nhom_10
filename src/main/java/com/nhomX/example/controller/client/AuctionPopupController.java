package com.nhomX.example.controller.client;


import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import javafx.application.Platform;

import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class AuctionPopupController implements ServerEventListener {

    @FXML
    private ImageView popupItemImageView;

    @FXML
    private Label lblPopupItemName;

    @FXML
    private Label lblPopupCurrentPrice;

    @FXML
    private Label lblPopupTimeLeft;

    @FXML
    private Label lblMinimumBidHint;

    @FXML
    private TextField txtBidAmount;

    @FXML
    private Label lblBidMessage;

    @FXML
    private Button btnSubmitBid;

    private static final long BID_STEP = 5_000L;

    private Auction currentAuction;
    private long currentPrice;
    private Timeline countdownTimer;
    private boolean isFormattingMoneyInput = false;
    /**
     * Callback trả kết quả về ItemCardController sau khi đặt giá thành công.
     * Tham số 1: giá mới.
     * Tham số 2: thời gian kết thúc mới, phòng trường hợp anti-sniping gia hạn phiên.
     */
    private BiConsumer<Long, LocalDateTime> onBidSuccess;

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;

        if (auction == null) {
            showError("Không có dữ liệu phiên đấu giá.");
            disableSubmitButton();
            return;
        }

        Items item = auction.getItem();
        if (item != null) {
            lblPopupItemName.setText(item.getTitle());
            loadItemImage(item);
        } else {
            lblPopupItemName.setText("Không rõ tên sản phẩm");
            ImageLoader.loadAsync(null, popupItemImageView);
        }

        currentPrice = Math.max(auction.getHighestBid(), auction.getStartingPrice());

        lblPopupCurrentPrice.setText(CurrencyFormatter.formatVND(currentPrice));
        lblMinimumBidHint.setText("Giá đấu của bạn chỉ cần lớn hơn giá hiện tại. "
                + "Các nút +5.000, +10.000, +20.000 là nút nhập nhanh từ giá hiện tại.");

        // Theo yêu cầu: ô nhập giá để trống, không tự nhập sẵn currentPrice + 5.000.
        txtBidAmount.clear();
        txtBidAmount.setPromptText("Nhập giá bạn muốn đặt");
        lblBidMessage.setText("");



        startCountdown();
        AuctionClient client = SessionManager.getInstance().getAuctionClient();

        if (client != null) {
            client.addListener(this);
        }
    }

    public void setOnBidSuccess(BiConsumer<Long, LocalDateTime> onBidSuccess) {
        this.onBidSuccess = onBidSuccess;
    }

    @FXML
    public void handleCloseAction(ActionEvent event) {
        closeWindow();
    }

    @FXML
    public void handleQuickBid1(ActionEvent event) {
        setQuickBid(BID_STEP);
    }

    @FXML
    public void handleQuickBid2(ActionEvent event) {
        setQuickBid(10_000L);
    }

    @FXML
    public void handleQuickBid3(ActionEvent event) {
        setQuickBid(20_000L);
    }

    @FXML
    public void handleSubmitBidAction(ActionEvent event) {
        if (currentAuction == null || currentAuction.getId() == null) {
            showError("Không tìm thấy mã phiên đấu giá.");
            return;
        }

        User currentUser = SessionManager.getInstance().getCurrentUser();

        if (currentUser == null || currentUser.getId() == null || currentUser.getId().isBlank()) {
            showError("Bạn cần đăng nhập trước khi đặt giá.");
            return;
        }

        long bidAmount;

        try {
            bidAmount = parseMoney(txtBidAmount.getText());
        } catch (NumberFormatException e) {
            showError("Vui lòng nhập số tiền hợp lệ. Ví dụ: 20000");
            return;
        }

        if (bidAmount <= currentPrice) {
            showError("Giá đấu phải lớn hơn giá hiện tại: "
                    + CurrencyFormatter.formatVND(currentPrice) + ".");
            return;
        }

        if (currentAuction.getEndTime() != null
                && LocalDateTime.now().isAfter(currentAuction.getEndTime())) {
            showError("Phiên đấu giá đã kết thúc.");
            return;
        }

        AuctionClient client = SessionManager.getInstance().getAuctionClient();

        if (client == null) {
            showError("Không có kết nối tới server.");
            return;
        }

        setSubmittingState(true);
        showSuccess("Đang gửi yêu cầu đặt giá...");

        /*
         * Controller KHÔNG ghi database trực tiếp nữa.
         * Controller chỉ gửi yêu cầu lên server.
         *
         * Server sẽ:
         * - kiểm tra giá
         * - kiểm tra số dư
         * - ghi bảng bids
         * - update bảng auctions
         * - trừ tiền người đặt mới
         * - hoàn tiền người dẫn đầu cũ
         * - broadcast UPDATE_PRICE cho tất cả client
         */
        client.placeBid(
                currentUser.getId(),
                currentAuction.getId(),
                bidAmount
        );
    }

    private void loadItemImage(Items item) {
        List<ItemImage> images = item.getImages();
        if (images != null && !images.isEmpty() && images.get(0).getImagePath() != null) {
            ImageLoader.loadAsync(images.get(0).getImagePath().trim(), popupItemImageView);
        } else {
            ImageLoader.loadAsync(null, popupItemImageView);
        }
    }

    private void setQuickBid(long extraMoney) {
        // Các nút +5.000, +10.000, +20.000 chỉ cộng từ giá hiện tại của phiên.
        // Ví dụ giá hiện tại 100.000, bấm +5.000 sẽ điền 105000.
        long quickBidAmount = currentPrice + extraMoney;
        txtBidAmount.setText(String.valueOf(quickBidAmount));
        lblBidMessage.setText("");
    }

    private long parseMoney(String text) {
        if (text == null) {
            throw new NumberFormatException("empty money");
        }

        String cleaned = text
                .replace("VNĐ", "")
                .replace("vnđ", "")
                .replace("VND", "")
                .replace("vnd", "")
                .replace(".", "")
                .replace(",", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty()) {
            throw new NumberFormatException("empty money");
        }

        return Long.parseLong(cleaned);
    }

    private void showError(String message) {
        lblBidMessage.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
        lblBidMessage.setText(message == null ? "Có lỗi xảy ra." : message);
    }

    private void showSuccess(String message) {
        lblBidMessage.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        lblBidMessage.setText(message);
    }

    private void disableSubmitButton() {
        if (btnSubmitBid != null) {
            btnSubmitBid.setDisable(true);
        }
    }

    private void setSubmittingState(boolean submitting) {
        if (btnSubmitBid != null) {
            btnSubmitBid.setDisable(submitting);
            btnSubmitBid.setText(submitting ? "Đang xử lý..." : "Xác nhận đấu giá");
        }
    }

    private void closeWindow() {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();

        if (client != null) {
            client.removeListener(this);
        }

        if (countdownTimer != null) {
            countdownTimer.stop();
        }

        if (txtBidAmount != null && txtBidAmount.getScene() != null) {
            Stage stage = (Stage) txtBidAmount.getScene().getWindow();
            stage.close();
        }
    }

    private void startCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }

        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTimeLeftLabel()));
        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        countdownTimer.play();
        updateTimeLeftLabel();
    }

    private void updateTimeLeftLabel() {
        if (currentAuction == null || currentAuction.getEndTime() == null) {
            lblPopupTimeLeft.setText("Không rõ");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = currentAuction.getEndTime();

        if (!now.isBefore(end)) {
            lblPopupTimeLeft.setText("Đã kết thúc");
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            return;
        }

        java.time.Duration duration = java.time.Duration.between(now, end);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) {
            lblPopupTimeLeft.setText(
                    String.format("%d ngày %02d:%02d:%02d", days, hours, minutes, seconds)
            );
        } else {
            lblPopupTimeLeft.setText(
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
            );
        }
    }
    @Override
    public void onBidResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            setSubmittingState(false);

            if (!isSuccess) {
                showError(message == null ? "Đặt giá thất bại." : message);
                return;
            }

            showSuccess(message == null ? "Đặt giá thành công!" : message);
            closeWindow();
        });
    }
    @Override
    public void onHighestBidUpdated(String auctionId, long newPrice, String bidderName) {
        Platform.runLater(() -> {
            if (currentAuction == null || currentAuction.getId() == null) {
                return;
            }

            if (!currentAuction.getId().equals(auctionId)) {
                return;
            }

            currentPrice = newPrice;
            currentAuction.setHighestBid(newPrice);

            lblPopupCurrentPrice.setText(CurrencyFormatter.formatVND(newPrice));

            lblMinimumBidHint.setText(
                    "Giá hiện tại vừa được cập nhật. Bạn cần đặt lớn hơn "
                            + CurrencyFormatter.formatVND(newPrice) + "."
            );

            /*
             * Nếu người khác vừa đặt giá trong lúc popup đang mở,
             * xóa ô nhập để người dùng không bấm nhầm giá cũ.
             */
            txtBidAmount.clear();
            txtBidAmount.setPromptText("Nhập giá lớn hơn " + CurrencyFormatter.formatVND(newPrice));
        });
    }
}

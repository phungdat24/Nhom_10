package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class ItemDetailController extends BaseController implements ServerEventListener, Initializable {
    @FXML
    private Label lblItemName;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblDescription;

    @FXML
    private ImageView imgItem;
    @FXML
    private TextField txtBidAmount;
    @FXML
    private Button btnBid;
    @FXML
    private Label lblStatusMessage;
    @FXML
    private LineChart<String, Number> priceChart;
    private XYChart.Series<String, Number> priceSeries;

    private Auction currentAuction;

    private AuctionClient auctionClient;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        if (txtBidAmount != null) {
            txtBidAmount.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue.isEmpty()) return;

                // Lọc bỏ ký tự không phải số
                String plainText = newValue.replaceAll("[^\\d]", "");
                if (plainText.isEmpty()) {
                    txtBidAmount.setText("");
                    return;
                }
                try {
                    long amount = Long.parseLong(plainText);
                    // Dùng class CurrencyFormatter:
                    String formattedText = CurrencyFormatter.formatNumber(amount);

                    if (!newValue.equals(formattedText)) {
                        txtBidAmount.setText(formattedText);
                        txtBidAmount.positionCaret(formattedText.length()); // Đưa trỏ chuột về cuối
                    }
                } catch (NumberFormatException e) {
                    txtBidAmount.setText(oldValue);
                }
            });
        }
    }

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;

        // Rút thông tin vật lý ra từ phiên đấu giá
        Items item = auction.getItem();

        lblItemName.setText(item.getTitle());

        // ✅ LẤY GIÁ CAO NHẤT TỪ CLASS AUCTION (Như em đã đề xuất!)
        lblCurrentPrice.setText(CurrencyFormatter.formatVND(auction.getHighestBid()));

        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Biến động giá");
        priceChart.getData().clear();
        priceChart.getData().add(priceSeries);

        // Lấy giờ hiện tại (Format thành String cho đẹp) làm trục X, giá khởi điểm làm trục Y
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        priceSeries.getData().add(new XYChart.Data<>(currentTime, auction.getHighestBid()));
        // Mô tả sản phẩm
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            lblDescription.setText(item.getDescription());
        } else {
            lblDescription.setText("Sản phẩm này chưa có mô tả chi tiết.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = auction.getStartTime();

        if (start != null && now.isBefore(start)) {
            // 1. CHƯA ĐẾN GIỜ: Khóa mõm các nút bấm
            txtBidAmount.setDisable(true);
            txtBidAmount.setPromptText("Chưa đến giờ đấu giá");

            if (btnBid != null) {
                btnBid.setDisable(true);
                btnBid.setText("Sắp diễn ra");
            }

            if (lblStatusMessage != null) {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
                lblStatusMessage.setText("Bắt đầu vào lúc: " + start.format(formatter));
                lblStatusMessage.setVisible(true);
            }
        } else {
            // 2. ĐÃ MỞ BÁN: Mở khóa cho người dùng tranh giành
            txtBidAmount.setDisable(false);
            if (btnBid != null) {
                btnBid.setDisable(false);
                btnBid.setText("Đấu giá");
            }
            if (lblStatusMessage != null) {
                lblStatusMessage.setVisible(false);
            }
        }
        List<ItemImage> images = item.getImages();

        if (images != null && !images.isEmpty() && images.get(0).getImagePath() != null) {
            String firstImagePath = images.get(0).getImagePath().trim();
            try {
                // Lấy ảnh từ thư mục resources
                Image img = new Image(getClass().getResourceAsStream(firstImagePath));
                imgItem.setImage(img);
            } catch (Exception e) {
                System.err.println("Không tìm thấy ảnh tại đường dẫn: " + firstImagePath);
                // (Tùy chọn) Có thể set một ảnh mặc định (Placeholder) nếu lỗi
                // imgItem.setImage(new Image(getClass().getResourceAsStream("/com/nhomX/example/images/default.png")));
            }
        } else {
            System.out.println("Món hàng này chưa có đường dẫn ảnh trong Database.");
        }
        // Gianh quyền sau vì nếu giành trước sẽ truyền các giá trị null gây sập server
        // Giành quyền
        auctionClient = SessionManager.getInstance().getAuctionClient();
        if (auctionClient != null) {
            auctionClient.setServerEventListener(this);
            // Báo cho Server bắt đầu zem:
            auctionClient.watchItem(currentAuction.getId());
        }
    }
        @FXML
        void handleBackAction (ActionEvent event) {
            if (auctionClient != null && currentAuction != null) {
                // Báo cho Server: "Tôi thoát đây, đừng gửi giá món này cho tôi nữa"
                auctionClient.unwatchItem(currentAuction.getId());
            }
            SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/dashboard.fxml");
    }

    @Override
    public void onHighestBidUpdated(String updatedItemId, long newPrice) {
        // CỰC KỲ QUAN TRỌNG: Phải kiểm tra xem giá mới gửi về có đúng là của món mình đang xem không?
        if (currentAuction != null && currentAuction.getId().equals(updatedItemId)) {

            // Cập nhật giá trên Model
            currentAuction.setHighestBid(newPrice);

            // Bọc trong Platform.runLater để giao cho luồng UI (Tránh Crash)
            javafx.application.Platform.runLater(() -> {
                lblCurrentPrice.setText(CurrencyFormatter.formatVND(newPrice));
                // THÊM ĐIỂM ẢNH MỚI VÀO BIỂU ĐỒ ĐƯỜNG
                String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                priceSeries.getData().add(new XYChart.Data<>(timeNow, newPrice));

                // Có thể làm hiệu ứng đổi màu nhấp nháy ở đây sau...
            });
        }
    }
    @FXML
    void handleBidAction(ActionEvent event){
        if(!SessionManager.getInstance().isLoggedIn()){
            AlertUtils.showWarning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để tham gia đấu giá!");
            return;
        }
        // 2. Lấy dữ liệu và lột bỏ dấu chấm
        String rawValue = txtBidAmount.getText().replace(".", "");
        if (rawValue.isEmpty()) {
            AlertUtils.showWarning("Lỗi", "Vui lòng nhập số tiền!");
            return;
        }

        try {
            long bidAmount = Long.parseLong(rawValue);

            // 3. Kiểm tra xem giá đặt có lớn hơn giá hiện tại không
            if (bidAmount <= currentAuction.getHighestBid()) {
                AlertUtils.showWarning("Lỗi đặt giá", "Giá đấu phải CAO HƠN giá hiện tại!");
                return;
            }

            // 4. Bắn lệnh lên Server
            if (auctionClient != null) {
                // ✅ FIX LỖI: Truyền đủ 3 tham số (userId, auctionId, bidAmount)
                String currentUserId = SessionManager.getInstance().getCurrentUser().getId();
                String auctionId = currentAuction.getId(); // Tạm thời dùng ItemID làm AuctionID
                auctionClient.placeBid(currentUserId, auctionId, bidAmount);
                System.out.println("CLIENT: Đã gửi lệnh đấu giá " + bidAmount + " cho món " + currentAuction.getId());

                // Xóa trắng ô nhập để chuẩn bị cho lần gõ tiếp theo
                txtBidAmount.clear();
            }else {
                // Thêm log và cảnh báo để dễ phát hiện lỗi
                AlertUtils.showError("Lỗi kết nối", "Hệ thống chưa kết nối được tới Server (Client null)!");
                System.err.println("❌ Lỗi: auctionClient chưa được truyền vào Controller này!");
            }
        } catch (NumberFormatException e) {
            AlertUtils.showError("Lỗi hệ thống", "Dữ liệu nhập không hợp lệ.");
            System.out.println("Lỗi đặt giá"+e.getMessage());
        }
    }
}



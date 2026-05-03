package com.nhomX.example.controller;

import com.nhomX.example.model.Items;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.ResourceBundle;

public class ItemDetailController implements ServerEventListener, Initializable {
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


    private Items currentItem;

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
                    double amount = Double.parseDouble(plainText);
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

    public void setItemData(Items item) {
        this.currentItem = item;

        lblItemName.setText(item.getTitle());
        lblCurrentPrice.setText(item.getCurrentPrice() + "VNĐ");
        // Mô tả sản phẩm
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            lblDescription.setText(item.getDescription());
        } else {
            lblDescription.setText("Sản phẩm này chưa có mô tả chi tiết.");
        }
        // Anh sản phẩm
        String imagePath = item.getImagePath();

        if (imagePath != null && !imagePath.trim().isEmpty()) {
            try {
                // Lấy ảnh từ thư mục resources
                Image img = new Image(getClass().getResourceAsStream(imagePath));
                imgItem.setImage(img);
            } catch (Exception e) {
                System.err.println("Không tìm thấy ảnh tại đường dẫn: " + imagePath);
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
            auctionClient.watchItem(item.getId());
        }
    }
        @FXML
        void handleBackAction (ActionEvent event) {
            if (auctionClient != null && currentItem != null) {
                // Báo cho Server: "Tôi thoát đây, đừng gửi giá món này cho tôi nữa"
                auctionClient.unwatchItem(currentItem.getId());
            }
            SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/dashboard.fxml");
    }

    @Override
    public void onPriceUpdated(String updatedItemId, double newPrice) {
        // CỰC KỲ QUAN TRỌNG: Phải kiểm tra xem giá mới gửi về có đúng là của món mình đang xem không?
        if (currentItem != null && currentItem.getId().equals(updatedItemId)) {

            // Cập nhật giá trên Model
            currentItem.setCurrentPrice(newPrice);

            // Bọc trong Platform.runLater để giao cho luồng UI (Tránh Crash)
            javafx.application.Platform.runLater(() -> {
                lblCurrentPrice.setText(CurrencyFormatter.formatVND(newPrice));

                // Có thể làm hiệu ứng đổi màu nhấp nháy ở đây sau...
            });
        }
    }
    @FXML
    void handleDashboard(ActionEvent event){

    }
    @FXML
    void handleLiveAuction(ActionEvent event) {
        System.out.println("Live Auction được nhấn");
    }

    @FXML
    void handleMyAuctions(ActionEvent event) {
        System.out.println("My Auctions được nhấn");
    }

    @FXML
    void handleProfile(ActionEvent event) {
        System.out.println("Profile được nhấn");
    }

    @FXML
    void handleSeller(ActionEvent event) {
        System.out.println("Seller được nhấn");
    }
    @FXML
    void handleLogin(ActionEvent event){
    }
    @FXML
    void handleLogout(ActionEvent event){

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
            double bidAmount = Double.parseDouble(rawValue);

            // 3. Kiểm tra xem giá đặt có lớn hơn giá hiện tại không
            if (bidAmount <= currentItem.getCurrentPrice()) {
                AlertUtils.showWarning("Lỗi đặt giá", "Giá đấu phải CAO HƠN giá hiện tại!");
                return;
            }

            // 4. Bắn lệnh lên Server
            if (auctionClient != null) {
                auctionClient.placeBid(currentItem.getId(), bidAmount);
                System.out.println("CLIENT: Đã gửi lệnh đấu giá " + bidAmount + " cho món " + currentItem.getId());

                // Xóa trắng ô nhập để chuẩn bị cho lần gõ tiếp theo
                txtBidAmount.clear();
            }
        } catch (NumberFormatException e) {
            AlertUtils.showError("Lỗi hệ thống", "Dữ liệu nhập không hợp lệ.");
            System.out.println("Lỗi đặt giá"+e.getMessage());
        }
    }
}



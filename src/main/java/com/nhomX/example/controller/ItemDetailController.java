package com.nhomX.example.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.nhomX.example.model.*;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ItemDetailController extends BaseController implements ServerEventListener {
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
    @FXML
    private VBox vboxBidHistory;
    @FXML
    private Label lblLeader;

    private Auction currentAuction;

    private AuctionClient auctionClient;


    @FXML
    public void initialize() {
        if (txtBidAmount != null) {
            txtBidAmount.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue.isEmpty())
                    return;

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
                java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
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
                // imgItem.setImage(new
                // Image(getClass().getResourceAsStream("/com/nhomX/example/images/default.png")));
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
            auctionClient.watchAuction(currentAuction.getId());
            // GỬI YÊU CẦU LẤY DỮ LIỆU CŨ
            auctionClient.getBidHistory(currentAuction.getId());
        }
    }

    @FXML
    void handleBackAction(ActionEvent event) {
        if (auctionClient != null && currentAuction != null) {
            clearServerListener();
            // Báo cho Server: "Tôi thoát đây, đừng gửi giá món này cho tôi nữa"
            auctionClient.unwatchAuction(currentAuction.getId());
        }
        if (MainDashBoardController.instance != null) {
            MainDashBoardController.instance
                    .loadView("/com/nhomX/example/fxml/LiveAuctionContent.fxml");
        } else {
            System.err.println("Lỗi: Không tìm thấy Quản gia MainDashBoardController!");
        }
    }

    @Override
    public void onHighestBidUpdated(String updatedItemId, long newPrice, String bidderName) {
        // CỰC KỲ QUAN TRỌNG: Phải kiểm tra xem giá mới gửi về có đúng là của món mình đang xem
        // không?
        if (currentAuction != null && currentAuction.getId().equals(updatedItemId)) {

            // Bọc trong Platform.runLater để giao cho luồng UI (Tránh Crash)
            Platform.runLater(() -> {
                currentAuction.setHighestBid(newPrice);
                lblCurrentPrice.setText(CurrencyFormatter.formatVND(newPrice));
                String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                String maskedName = maskName(bidderName);
                lblLeader.setText("\uD83C\uDFC6 Người dẫn đầu: "+ maskedName);
                // TẠO DÒNG LỊCH SỬ MỚI
                Node newRow = createBidRow(maskedName, newPrice, timeNow);

                // THÊM VÀO VBOX (Chèn vào vị trí 0 để người mới nhất luôn nằm trên cùng)
                vboxBidHistory.getChildren().add(0, newRow);
                // THÊM ĐIỂM ẢNH MỚI VÀO BIỂU ĐỒ ĐƯỜNG

                priceSeries.getData().add(new XYChart.Data<>(timeNow, newPrice));
                // [ÁP DỤNG THUẬT TOÁN]: Xóa điểm CŨ NHẤT nếu vượt quá 7
                if (priceSeries.getData().size() > 7) {
                    priceSeries.getData().remove(0);
                }

                // Có thể làm hiệu ứng đổi màu nhấp nháy ở đây sau...
            });
        }
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
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
                System.out.println("CLIENT: Đã gửi lệnh đấu giá " + bidAmount + " cho món "
                        + currentAuction.getId());

                // Xóa trắng ô nhập để chuẩn bị cho lần gõ tiếp theo
                txtBidAmount.clear();
            } else {
                // Thêm log và cảnh báo để dễ phát hiện lỗi
                AlertUtils.showError("Lỗi kết nối",
                        "Hệ thống chưa kết nối được tới Server (Client null)!");
                System.err.println("❌ Lỗi: auctionClient chưa được truyền vào Controller này!");
            }
        } catch (NumberFormatException e) {
            AlertUtils.showError("Lỗi hệ thống", "Dữ liệu nhập không hợp lệ.");
            System.out.println("Lỗi đặt giá" + e.getMessage());
        }
    }

    @Override
    public void onBidHistoryReceived(List<BidTransaction> history) {
        // [TỐI ƯU 1]: TẮT hiệu ứng chuyển động tạm thời để nạp dữ liệu cái rụp, không bị giật lag
        priceChart.setAnimated(false);
        Platform.runLater(() -> {
            // 1. Dọn dẹp giao diện trước khi đổ dữ liệu mới
            vboxBidHistory.getChildren().clear();
            priceSeries.getData().clear();

            if (history == null || history.isEmpty()) {
                lblLeader.setText("🏆 Người dẫn đầu: Chưa có");
                // Biểu đồ neo tạm bằng giá trần hiện tại nếu rỗng
                String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                priceSeries.getData().add(new XYChart.Data<>(timeNow, currentAuction.getHighestBid()));
                priceChart.setAnimated(true);
                return;
            }

            // 1. Sort Cũ -> Mới
            history.sort((b1, b2) -> b1.getBidTime().compareTo(b2.getBidTime()));

            for (BidTransaction bid : history) {
                String timeStr = bid.getBidTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                // Thêm vào biểu đồ
                priceSeries.getData().add(new XYChart.Data<>(timeStr, bid.getAmount()));

                String fullName = bid.getBidder().getFullName() != null ? bid.getBidder().getFullName() : bid.getBidder().getUserName();
                String maskedName = maskName(fullName);
                Node row = createBidRow(maskedName, bid.getAmount(), timeStr);

                vboxBidHistory.getChildren().add(0, row);
            }

            // [TỐI ƯU 2] THUẬT TOÁN CỬA SỔ TRƯỢT: Chặt bỏ các điểm cũ để giữ đúng 7 điểm
            // Vòng lặp này sẽ xóa liên tục các điểm ở vị trí 0 cho đến khi size <= 7
            while (priceSeries.getData().size() > 7) {
                priceSeries.getData().remove(0);
            }

            BidTransaction winningBid = history.get(history.size() - 1);
            long latestPrice = winningBid.getAmount();

            lblCurrentPrice.setText(CurrencyFormatter.formatVND(latestPrice));
            currentAuction.setHighestBid(latestPrice);

            String leaderFullName = winningBid.getBidder().getFullName() != null ? winningBid.getBidder().getFullName() : winningBid.getBidder().getUserName();
            String maskedLeader = maskName(leaderFullName);
            lblLeader.setText("🏆 Người dẫn đầu: " + maskedLeader);

            // [TỐI ƯU 3]: BẬT LẠI hiệu ứng chuyển động để sẵn sàng đón các lệnh đặt giá Realtime
            priceChart.setAnimated(true);
        });
    }
    @Override
    public void onAuctionClosed(String auctionId, String winnerId) {
        // Chỉ xử lý nếu gói tin đúng là của món hàng đang xem
        if (currentAuction != null && currentAuction.getId().equals(auctionId)) {
            Platform.runLater(() -> {
                // Khóa ngay lập tức mọi tương tác
                txtBidAmount.setDisable(true);
                if (btnBid != null) {
                    btnBid.setDisable(true);
                    btnBid.setText("ĐÃ KẾT THÚC");
                }

                // Cập nhật trạng thái
                currentAuction.setStatus(AuctionStatus.FINISHED);

                // Hiển thị người chiến thắng
                if (winnerId != null && !winnerId.isEmpty()) {
                    // Cần map winnerId ra tên hoặc dùng luôn nếu đã là tên
                    lblLeader.setText("🏆 PHIÊN ĐÃ ĐÓNG! Người thắng: " + winnerId);
                    lblLeader.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    lblLeader.setText("🏆 PHIÊN ĐÃ ĐÓNG! Không có người mua.");
                }

                AlertUtils.showSuccess("Kết thúc", "Phiên đấu giá đã chính thức khép lại!");
            });
        }
    }

    private Node createBidRow(String bidderName, long amount, String timeStr) {
        // 1. Tạo hộp ngang chứa các thành phần
        HBox row = new HBox(15);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 10; -fx-border-color: #f1f2f6; -fx-border-width: 0 0 1 0;");

        // 2. Icon và Tên người đặt giá
        Label lblUser = new Label("👤 " + (bidderName != null ? bidderName : "Ẩn danh"));
        lblUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #2f3542;");

        // 3. Spacer để đẩy giá và thời gian sang bên phải
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 4. Số tiền (Sử dụng CurrencyFormatter đã có của bạn)
        Label lblAmount = new Label(CurrencyFormatter.formatVND(amount));
        lblAmount.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");

        // 5. Thời gian đặt giá
        Label lblTime = new Label(timeStr);
        lblTime.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 11px;");

        row.getChildren().addAll(lblUser, spacer, lblAmount, lblTime);
        return row;
    }
    /**
     * Hàm xử lý che giấu danh tính người dùng (Data Masking / Anonymization)
     */
    private String maskName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Ẩn danh";
        fullName = fullName.trim();

        // 1. Trường hợp là Email (Ví dụ: 25021715@vnu.edu.vn)
        if (fullName.contains("@")) {
            String[] parts = fullName.split("@");
            String emailName = parts[0];
            String domain = parts.length > 1 ? parts[1] : "";

            // Che tên email, chỉ để lại 3 chữ cái đầu (VD: 250***@vnu.edu.vn)
            if (emailName.length() <= 3) return emailName + "***@" + domain;
            return emailName.substring(0, 3) + "***@" + domain;
        }

        // 2. Trường hợp là Tên đầy đủ (Ví dụ: Nguyễn Văn Bản)
        String[] parts = fullName.split("\\s+");
        if (parts.length == 1) {
            // Tên chỉ có 1 chữ (Ví dụ: Dat -> D***t)
            if (fullName.length() <= 2) return fullName + "***";
            return fullName.charAt(0) + "***" + fullName.charAt(fullName.length() - 1);
        } else {
            // Tên có nhiều chữ: Lấy chữ đầu + *** + chữ cuối
            String firstWord = parts[0];
            String lastWord = parts[parts.length - 1];
            return firstWord + " *** " + lastWord;
        }
    }

}



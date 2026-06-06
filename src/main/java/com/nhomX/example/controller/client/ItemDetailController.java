package com.nhomX.example.controller.client;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class ItemDetailController extends BaseController implements ServerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(ItemDetailController.class);
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
    @FXML
    private HBox hboxThumbnails;
    @FXML
    private StackPane imageContainer;
    // Thêm vào phần khai báo FXML:
    @FXML
    private TextField txtMaxAutoBid; // Giá tối đa
    @FXML
    private TextField txtAutoBidStep; // Bước giá mỗi lần
    @FXML
    private CheckBox chkAutoBidToggle;
    @FXML
    private Label lblAutoBidStatus;
    @FXML
    private Button btnSetupAutoBid;
    // Cờ trạng thái Auto-bid hiện tại
    private boolean isAutoBidActive = false;

    // Slideshow
    private Timeline slideshowTimeline;
    private int slideshowIndex = 0;
    private List<ItemImage> slideshowImages; // Lưu lại list ảnh để Timeline dùng


    private String currentAuctionId;

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
        if (priceChart != null && priceChart.getYAxis() instanceof javafx.scene.chart.NumberAxis) {
            NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();

            // Tắt chế độ ép buộc trục Y phải bắt đầu từ số 0
            yAxis.setForceZeroInRange(false);

            // Bật tự động căn chỉnh khoảng cách để đồ thị luôn đẹp
            yAxis.setAutoRanging(true);
        }
        if (imageContainer != null) {
            /*
             * Tạo Rectangle clip KHÔNG có kích thước cố định. Bind width/height vào imageContainer
             * để clip co giãn cùng container. Bind arcWidth/arcHeight cố định để góc bo luôn đồng
             * đều.
             */
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();

            // Bind kích thước clip theo container — tự động cập nhật khi layout thay đổi
            clip.widthProperty().bind(imageContainer.widthProperty());
            clip.heightProperty().bind(imageContainer.heightProperty());

            // Bo góc cố định 16px — không phụ thuộc kích thước
            clip.setArcWidth(16);
            clip.setArcHeight(16);

            imageContainer.setClip(clip);

            /*
             * Bind fitWidth và fitHeight của ImageView vào container. - subtract(4): trừ 4px
             * padding để ảnh không sát viền clip - preserveRatio=true trong FXML đảm bảo ảnh không
             * bị méo dù width != height
             */
            if (imgItem != null) {
                imgItem.fitWidthProperty().bind(imageContainer.widthProperty().subtract(4));
                imgItem.fitHeightProperty().bind(imageContainer.heightProperty().subtract(4));
            }
        }
        // TỐI ƯU UX AUTO-BID
        if (chkAutoBidToggle != null) {
            // 1. Trói buộc 2 ô nhập liệu vào trạng thái của công tắc (Ngược lại với selected)
            if (txtMaxAutoBid != null) {
                txtMaxAutoBid.disableProperty().bind(chkAutoBidToggle.selectedProperty().not());
            }
            if (txtAutoBidStep != null) {
                txtAutoBidStep.disableProperty().bind(chkAutoBidToggle.selectedProperty().not());
            }

            // 2. Lắng nghe sự thay đổi của công tắc để đổi text nút bấm
            chkAutoBidToggle.selectedProperty().addListener((observable, oldValue, isNowActive) -> {
                if (btnSetupAutoBid != null) {
                    if (isNowActive) {
                        btnSetupAutoBid.setText("Lưu thiết lập Auto-bid");
                        btnSetupAutoBid
                                .setStyle("-fx-background-color: #bfa173; -fx-text-fill: white;");
                    } else {
                        btnSetupAutoBid.setText("Hủy Auto-bid");
                        btnSetupAutoBid
                                .setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                    }
                }
            });
        }
    }

    public void setAuctionData(Auction initialAuction) {
        // Chỉ lưu ID để làm "chìa khóa"
        this.currentAuctionId = initialAuction.getId();
        // Luôn lấy bản mới nhất từ Cache ngay khi vừa mở giao diện
        Auction freshAuction = AuctionManager.getInstance().getAuctionById(this.currentAuctionId);
        if (freshAuction == null)
            freshAuction = initialAuction; // Đề phòng bất trắc

        // Rút thông tin vật lý ra từ phiên đấu giá
        Items item = freshAuction.getItem();

        lblItemName.setText(item.getTitle());

        // ✅ LẤY GIÁ CAO NHẤT TỪ CLASS AUCTION (Như em đã đề xuất!)
        lblCurrentPrice.setText(CurrencyFormatter.formatVND(freshAuction.getHighestBid()));

        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Biến động giá");
        priceChart.getData().clear();
        priceChart.getData().add(priceSeries);

        // Mô tả sản phẩm
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            lblDescription.setText(item.getDescription());
        } else {
            lblDescription.setText("Sản phẩm này chưa có mô tả chi tiết.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = freshAuction.getStartTime();

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
        // TẬP TRUNG HÓA VIỆC NẠP ẢNH
        this.slideshowImages = item.getImages(); // Nạp vào đây là xong
        setupSlideshow(this.slideshowImages); // Gọi hàm setup này
        // Gianh quyền sau vì nếu giành trước sẽ truyền các giá trị null gây sập server
        // Giành quyền
        auctionClient = SessionManager.getInstance().getAuctionClient();
        if (auctionClient != null) {
            auctionClient.setServerEventListener(this);
            // Báo cho Server bắt đầu zem:
            auctionClient.watchAuction(this.currentAuctionId);
            // GỬI YÊU CẦU LẤY DỮ LIỆU CŨ
            auctionClient.getBidHistory(this.currentAuctionId);
        }
    }

    @FXML
    void handleBackAction(ActionEvent event) {
        // [THÊM] Dừng slideshow trước khi thoát — chống Memory Leak và CPU leak
        stopSlideshow();
        if (auctionClient != null && currentAuctionId != null) {
            clearServerListener();
            // Báo cho Server: "Tôi thoát đây, đừng gửi giá món này cho tôi nữa"
            auctionClient.unwatchAuction(currentAuctionId);
        }
        if (MainDashBoardController.instance != null) {
            // 🛠 KIẾN TRÚC MỚI: Bật ngược lại màn hình cũ từ RAM
            MainDashBoardController.instance.restorePreviousView();
        } else {
            logger.error("Không tìm thấy MainDashBoardController");
        }
    }

    @Override
    public void onHighestBidUpdated(String updatedItemId, long newPrice, String bidderName) {
        // CỰC KỲ QUAN TRỌNG: Phải kiểm tra xem giá mới gửi về có đúng là của món mình
        // đang xem
        // không?
        if (currentAuctionId != null && currentAuctionId.equals(updatedItemId)) {

            // Bọc trong Platform.runLater để giao cho luồng UI (Tránh Crash)
            Platform.runLater(() -> {
                // [REFACTOR 3]: Không dùng hàm setHighestBid tự chế nữa.
                // Kéo giá mới từ AuctionManager (nơi đã được Socket nạp dữ liệu an toàn)
                Auction freshAuction =
                        AuctionManager.getInstance().getAuctionById(currentAuctionId);
                if (freshAuction != null) {
                    lblCurrentPrice
                            .setText(CurrencyFormatter.formatVND(freshAuction.getHighestBid()));
                }
                String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                String maskedName = maskName(bidderName);
                lblLeader.setText("\uD83C\uDFC6 Người dẫn đầu: " + maskedName);
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

            // [REFACTOR 2]: Lấy giá tươi mới nhất từ Nguồn Sự Thật để kiểm duyệt trước khi
            // bắn lệnh
            Auction freshAuction =
                    AuctionManager.getInstance().getAuctionById(this.currentAuctionId);
            if (freshAuction != null && bidAmount <= freshAuction.getHighestBid()) {
                AlertUtils.showWarning("Lỗi đặt giá", "Giá đấu phải CAO HƠN giá hiện tại!");
                return;
            }

            // 4. Bắn lệnh lên Server
            if (auctionClient != null) {
                // ✅ FIX LỖI: Truyền đủ 3 tham số (userId, auctionId, bidAmount)
                String currentUserId = SessionManager.getInstance().getCurrentUser().getId();
                auctionClient.placeBid(currentUserId, this.currentAuctionId, bidAmount);
                logger.info("CLIENT: Đã gửi lệnh đấu giá {} cho món {}", bidAmount,
                        this.currentAuctionId);

                // Xóa trắng ô nhập để chuẩn bị cho lần gõ tiếp theo
                txtBidAmount.clear();
            } else {
                // Thêm log và cảnh báo để dễ phát hiện lỗi
                AlertUtils.showError("Lỗi kết nối",
                        "Hệ thống chưa kết nối được tới Server (Client null)!");
                logger.error("❌ Lỗi: auctionClient chưa được truyền vào Controller này!");
            }
        } catch (NumberFormatException e) {
            AlertUtils.showError("Lỗi hệ thống", "Dữ liệu nhập không hợp lệ.");
            logger.warn("Lỗi đặt giá: {}", e.getMessage());
        }
    }

    @Override
    public void onBidHistoryReceived(List<BidTransaction> history) {
        // [TỐI ƯU 1]: TẮT hiệu ứng chuyển động tạm thời để nạp dữ liệu cái rụp, không
        // bị giật lag
        priceChart.setAnimated(false);
        Platform.runLater(() -> {
            // 1. Dọn dẹp giao diện trước khi đổ dữ liệu mới
            vboxBidHistory.getChildren().clear();
            priceSeries.getData().clear();

            // [REFACTOR 1]: Gọi Nguồn Sự Thật Duy Nhất ra
            Auction freshAuction = AuctionManager.getInstance().getAuctionById(currentAuctionId);
            if (freshAuction == null)
                return; // Bảo vệ an toàn

            // Lấy giá khởi điểm gốc từ Item
            long startingPrice = freshAuction.getStartingPrice();

            // Lấy thời gian bắt đầu phiên làm mốc X (Nếu null thì lấy giờ hiện tại làm mốc tạm)
            LocalDateTime startTime = freshAuction.getStartTime();
            String startTimeStr =
                    (startTime != null) ? startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                            : LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // LUÔN LUÔN nạp điểm gốc này vào biểu đồ đầu tiên
            priceSeries.getData().add(new XYChart.Data<>(startTimeStr, startingPrice));

            if (history == null || history.isEmpty()) {
                lblLeader.setText("🏆 Người dẫn đầu: Chưa có");
                priceChart.setAnimated(true);
                return;
            }

            // 1. Sort Cũ -> Mới
            history.sort((b1, b2) -> b1.getBidTime().compareTo(b2.getBidTime()));

            for (BidTransaction bid : history) {
                String timeStr = bid.getBidTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                // Thêm vào biểu đồ
                priceSeries.getData().add(new XYChart.Data<>(timeStr, bid.getAmount()));

                String fullName =
                        bid.getBidder().getFullName() != null ? bid.getBidder().getFullName()
                                : bid.getBidder().getUserName();
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
            // [REFACTOR 3]: ĐÃ XÓA dòng currentAuction.setHighestBid(latestPrice)
            // Lý do: Việc cập nhật giá trị cao nhất vào RAM là nhiệm vụ của Manager, Giao diện
            // (Controller) chỉ lo hiển thị.
            String leaderFullName = winningBid.getBidder().getFullName() != null
                    ? winningBid.getBidder().getFullName()
                    : winningBid.getBidder().getUserName();
            String maskedLeader = maskName(leaderFullName);
            lblLeader.setText("🏆 Người dẫn đầu: " + maskedLeader);

            // [TỐI ƯU 3]: BẬT LẠI hiệu ứng chuyển động để sẵn sàng đón các lệnh đặt giá Realtime
            priceChart.setAnimated(true);
        });
    }

    private void setupSlideshow(List<ItemImage> images) {
        // Dừng slideshow cũ nếu đang chạy (ví dụ: gọi setAuctionData() nhiều lần)
        stopSlideshow();

        // Trường hợp 1: Không có ảnh nào → hiển thị placeholder
        if (images == null || images.isEmpty()) {
            ImageLoader.loadAsync(null, imgItem);
            return;
        }

        this.slideshowImages = images;
        setupThumbnails(images);
        this.slideshowIndex = 0;
        // Load ảnh đầu tiên ngay lập tức, không chờ 2 giây
        loadSlideshowImage(0);

        // Trường hợp 2: Chỉ có 1 ảnh → dừng lại, không tạo Timeline
        if (images.size() == 1)
            return;
        // Trường hợp 3: Nhiều ảnh → tạo Timeline chuyển ảnh mỗi 2 giây
        slideshowTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(3), event -> {
            // [FIX BUG] Tăng index TRƯỚC khi load — bug cũ không tăng index
            slideshowIndex = (slideshowIndex + 1) % slideshowImages.size();
            loadSlideshowImageWithFade(slideshowIndex);
        }));

        slideshowTimeline.setCycleCount(Animation.INDEFINITE); // Chạy mãi mãi
        slideshowTimeline.play();
        logger.info("SLIDESHOW: Bắt đầu với {} ảnh.", images.size());
    }

    /**
     * Load ảnh tại index chỉ định qua ImageLoader (đúng kiến trúc Client-Server). Tái sử dụng
     * ImageLoader.loadAsync() theo ràng buộc kỹ thuật.
     */
    private void loadSlideshowImage(int index) {
        if (slideshowImages == null || index >= slideshowImages.size())
            return;
        String fileName = slideshowImages.get(index).getImagePath();
        // Tái sử dụng ImageLoader.loadAsync() — đúng ràng buộc kỹ thuật
        ImageLoader.loadAsync(fileName != null ? fileName.trim() : null, imgItem);
        updateThumbnailSelection();
    }

    /**
     * Load ảnh tại index với hiệu ứng cross-fade mượt mà. Fade out ảnh cũ → thay ảnh mới → Fade in.
     */
    private void loadSlideshowImageWithFade(int index) {
        if (slideshowImages == null || index >= slideshowImages.size())
            return;
        String fileName = slideshowImages.get(index).getImagePath();
        if (fileName == null || fileName.isBlank())
            return;

        // Bước 1: Fade out ảnh hiện tại (1.0 → 0.2 trong 200ms)
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), imgItem);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.2);

        fadeOut.setOnFinished(ev -> {
            // Bước 2: Dùng ImageLoader để tải ảnh mới qua mạng
            // Callback của ImageLoader đã chạy trên UI thread — an toàn
            ImageLoader.loadAsync(fileName.trim(), imgItem);

            // Bước 3: Fade in ảnh mới (0.2 → 1.0 trong 200ms)
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), imgItem);
            fadeIn.setFromValue(0.2);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        fadeOut.play();
    }

    /**
     * Dừng và dọn dẹp Timeline slideshow. BẮT BUỘC gọi khi thoát màn hình để tránh Memory Leak.
     */
    private void stopSlideshow() {
        if (slideshowTimeline != null) {
            slideshowTimeline.stop();
            slideshowTimeline = null;
            logger.info("SLIDESHOW: Đã dừng và dọn dẹp.");
        }
    }

    @Override
    public void onAuctionClosed(String auctionId, String winnerId) {
        // Chỉ xử lý nếu gói tin đúng là của món hàng đang xem
        if (currentAuctionId != null && currentAuctionId.equals(auctionId)) {
            Platform.runLater(() -> {
                // Khóa ngay lập tức mọi tương tác
                txtBidAmount.setDisable(true);
                if (btnBid != null) {
                    btnBid.setDisable(true);
                    btnBid.setText("ĐÃ KẾT THÚC");
                }
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
        if (fullName == null || fullName.trim().isEmpty())
            return "Ẩn danh";
        fullName = fullName.trim();

        // 1. Trường hợp là Email (Ví dụ: 25021715@vnu.edu.vn)
        if (fullName.contains("@")) {
            String[] parts = fullName.split("@");
            String emailName = parts[0];
            String domain = parts.length > 1 ? parts[1] : "";

            // Che tên email, chỉ để lại 3 chữ cái đầu (VD: 250***@vnu.edu.vn)
            if (emailName.length() <= 3)
                return emailName + "***@" + domain;
            return emailName.substring(0, 3) + "***@" + domain;
        }

        // 2. Trường hợp là Tên đầy đủ (Ví dụ: Nguyễn Văn Bản)
        String[] parts = fullName.split("\\s+");
        if (parts.length == 1) {
            // Tên chỉ có 1 chữ (Ví dụ: Dat -> D***t)
            if (fullName.length() <= 2)
                return fullName + "***";
            return fullName.charAt(0) + "***" + fullName.charAt(fullName.length() - 1);
        } else {
            // Tên có nhiều chữ: Lấy chữ đầu + *** + chữ cuối
            String firstWord = parts[0];
            String lastWord = parts[parts.length - 1];
            return firstWord + " *** " + lastWord;
        }
    }

    @Override
    public void onBidResult(boolean isSuccess, String message) {
        // Luôn phải bọc trong Platform.runLater khi muốn hiển thị Popup UI
        Platform.runLater(() -> {
            if (isSuccess) {
                AlertUtils.showSuccess("Thành công", message);
                // (Tuỳ chọn) Nếu thành công thì xóa số tiền vừa nhập
                if (txtBidAmount != null) {
                    txtBidAmount.clear();
                }
            } else {
                AlertUtils.showError("Lỗi đặt giá", message);
            }
        });
    }

    /**
     * Tạo dải ảnh thu nhỏ (Thumbnails) phía dưới ảnh chính
     */
    private void setupThumbnails(List<ItemImage> images) {
        if (hboxThumbnails == null || images == null)
            return;
        hboxThumbnails.getChildren().clear();

        for (int i = 0; i < images.size(); i++) {
            String imgPath = images.get(i).getImagePath();

            // Tạo khung chứa Thumbnail
            StackPane thumbWrapper = new StackPane();
            thumbWrapper.setPrefSize(60, 60);
            thumbWrapper.setMinSize(60, 60);
            thumbWrapper.setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #f1f2f6; -fx-background-radius: 5; -fx-cursor: hand;");

            // Tạo ImageView cho Thumbnail
            ImageView thumbView = new ImageView();
            thumbView.setFitWidth(50);
            thumbView.setFitHeight(50);
            thumbView.setPreserveRatio(true);

            // Nạp ảnh bằng ImageLoader
            ImageLoader.loadAsync(imgPath != null ? imgPath.trim() : null, thumbView);
            thumbWrapper.getChildren().add(thumbView);

            // Bắt sự kiện khi người dùng click vào ảnh nhỏ
            final int clickedIndex = i;
            thumbWrapper.setOnMouseClicked(e -> {
                slideshowIndex = clickedIndex;
                loadSlideshowImageWithFade(slideshowIndex);

                // Khởi động lại Timer để tránh việc vừa click xong thì Timeline nhảy sang ảnh khác
                if (slideshowTimeline != null) {
                    slideshowTimeline.playFromStart();
                }
            });

            hboxThumbnails.getChildren().add(thumbWrapper);
        }
    }

    /**
     * Cập nhật viền (border) cho Thumbnail đang hiển thị
     */
    private void updateThumbnailSelection() {
        if (hboxThumbnails == null || hboxThumbnails.getChildren().isEmpty())
            return;

        for (int i = 0; i < hboxThumbnails.getChildren().size(); i++) {
            Node node = hboxThumbnails.getChildren().get(i);
            if (i == slideshowIndex) {
                // Đổi viền sang màu cam (#e67e22) khi được chọn
                node.setStyle(
                        "-fx-border-color: #e67e22; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #ffffff; -fx-background-radius: 5; -fx-cursor: hand;");
            } else {
                // Xóa viền khi không được chọn
                node.setStyle(
                        "-fx-border-color: transparent; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #f1f2f6; -fx-background-radius: 5; -fx-cursor: hand;");
            }
        }
    }

    /** Cập nhật trạng thái UI Auto-bid một cách nhất quán. */
    private void updateAutoBidUI(boolean isActive, String statusText) {
        this.isAutoBidActive = isActive;
        if (lblAutoBidStatus != null) {
            lblAutoBidStatus.setText(statusText);
            lblAutoBidStatus.setStyle(isActive ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                    : "-fx-text-fill: #7f8c8d;");
        }
        // Lock/unlock input fields
        if (txtMaxAutoBid != null)
            txtMaxAutoBid.setDisable(isActive);
        if (txtAutoBidStep != null)
            txtAutoBidStep.setDisable(isActive);
    }

    @FXML
    public void handleSetupAutoBid(ActionEvent event) {
        // Guard: Phải đăng nhập
        if (!SessionManager.getInstance().isLoggedIn()) {
            AlertUtils.showWarning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để sử dụng Auto-bid!");
            if (chkAutoBidToggle != null)
                chkAutoBidToggle.setSelected(false);
            return;
        }

        // Guard: Phải đang xem một phiên hợp lệ
        if (currentAuctionId == null)
            return;

        boolean wantActivate = chkAutoBidToggle != null && chkAutoBidToggle.isSelected();
        if (!wantActivate) {
            // Người dùng TẮT Auto-bid — gửi lệnh với maxPrice=0 để Server hiểu là deactivate
            if (auctionClient != null) {
                auctionClient.setupAutoBid(currentAuctionId, 0L, 0L);
            }
            updateAutoBidUI(false, "Auto-bid đã tắt.");
            return;
        }

        // Người dùng BẬT Auto-bid — validate input
        String rawMax =
                txtMaxAutoBid != null ? txtMaxAutoBid.getText().replaceAll("[^\\d]", "") : "";
        String rawStep =
                txtAutoBidStep != null ? txtAutoBidStep.getText().replaceAll("[^\\d]", "") : "";

        if (rawMax.isEmpty() || rawStep.isEmpty()) {
            AlertUtils.showWarning("Thiếu thông tin",
                    "Vui lòng nhập Giá tối đa và Bước giá trước khi bật Auto-bid.");
            if (chkAutoBidToggle != null)
                chkAutoBidToggle.setSelected(false);
            return;
        }
        long maxPrice, stepPrice;
        try {
            maxPrice = Long.parseLong(rawMax);
            stepPrice = Long.parseLong(rawStep);
        } catch (NumberFormatException e) {
            AlertUtils.showError("Lỗi", "Giá không hợp lệ!");
            if (chkAutoBidToggle != null)
                chkAutoBidToggle.setSelected(false);
            return;
        }

        // Validate nghiệp vụ phía Client (Server vẫn validate lại — Defense in Depth)
        Auction fresh = AuctionManager.getInstance().getAuctionById(currentAuctionId);
        long currentHighest = fresh != null ? fresh.getHighestBid() : 0L;

        if (maxPrice <= currentHighest) {
            AlertUtils.showWarning("Giá không hợp lệ", "Giá tối đa phải cao hơn giá hiện tại ("
                    + CurrencyFormatter.formatVND(currentHighest) + ")!");
            if (chkAutoBidToggle != null)
                chkAutoBidToggle.setSelected(false);
            return;
        }
        if (stepPrice <= 0) {
            AlertUtils.showWarning("Bước giá không hợp lệ", "Bước giá phải lớn hơn 0!");
            if (chkAutoBidToggle != null)
                chkAutoBidToggle.setSelected(false);
            return;
        }

        // Gửi lệnh lên Server
        if (auctionClient != null) {
            auctionClient.setupAutoBid(currentAuctionId, maxPrice, stepPrice);
            updateAutoBidUI(true,
                    "Auto-bid đang bật: Tối đa " + CurrencyFormatter.formatVND(maxPrice)
                            + " | Bước " + CurrencyFormatter.formatVND(stepPrice));
            logger.info("AUTO-BID: Gửi setup max={} step={}", maxPrice, stepPrice);
        }
    }

    // THÊM HÀM NÀY VÀO TRONG ItemDetailController.java
    @Override
    public void onAutoBidStopped(String auctionId, String reason) {
        // Chỉ xử lý nếu đúng là món hàng mình đang xem
        if (this.currentAuctionId != null && this.currentAuctionId.equals(auctionId)) {

            // 1. Nhả nút tick box ra
            if (chkAutoBidToggle != null) {
                chkAutoBidToggle.setSelected(false);
            }

            // 2. Mở khóa ô nhập và đổi trạng thái chữ thành màu đỏ/xám
            updateAutoBidUI(false, "Đã dừng: Vượt mức giá tối đa.");

            // 3. Hiện Popup thông báo cho người dùng biết để họ nhập mức giá mới
            AlertUtils.showWarning("Auto-bid kết thúc",
                    reason + "\nVui lòng thiết lập lại nếu muốn tiếp tục đấu giá.");
        }
    }
}

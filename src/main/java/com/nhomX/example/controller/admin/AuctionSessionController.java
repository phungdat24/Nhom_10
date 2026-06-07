package com.nhomX.example.controller.admin;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionSessionController implements Initializable, ServerEventListener {

    @FXML
    private TextField searchField;
    @FXML
    private VBox tableBody;
    @FXML
    private Label sortPriceHeader;
    @FXML
    private Label sortTimeHeader;
    @FXML
    private Label totalRevenueLabel;
    @FXML
    private Label liveSessionsLabel;
    @FXML
    private Label pageInfoLabel;
    @FXML
    private Label liveBadgeLabel;
    @FXML
    private Label totalBidsLabel;

    private AuctionClient client;


    private final NumberFormat vndFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    // Bộ não xử lý RAM:
    private final List<Auction> allLiveAuctions = new ArrayList<>();
    private List<Auction> displayedAuctions = new ArrayList<>();

    // BỘ CACHE GIẢI CỨU GIAO DIỆN (UI Freeze Preventer)
    // Cache nguyên cả HBox để khỏi vẽ lại mỗi khi filter
    private final Map<String, HBox> rowCache = new HashMap<>();
    // Cache riêng cái Label thời gian để update từng giây siêu tốc với độ phức tạp O(1)
    private final Map<String, Label> timeLabelCache = new HashMap<>();

    // Trạng thái Sorting
    private boolean isSortPriceAsc = true;
    private boolean isSortTimeAsc = true;
    private int totalBidsCounter = 0; // Biến đếm lượt đấu giá
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.addListener(this);
            // Yêu cầu Server gửi danh sách các phiên đang PENDING/OPEN
            client.sendToServer(new Message("GET_LIVE_AUCTIONS"));
        }

        setupSearchFilter();
        setupSorting();
    }

    // ==========================================
    // 1. RAM FILTERING & SORTING
    // ==========================================
    private void setupSearchFilter() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            applyFilterAndSort();
        });
    }

    private void setupSorting() {
        sortPriceHeader.setOnMouseClicked(e -> {
            isSortPriceAsc = !isSortPriceAsc;
            sortPriceHeader.setText("GIÁ TRẦN HIỆN TẠI " + (isSortPriceAsc ? "↑" : "↓"));
            sortTimeHeader.setText("THỜI GIAN ↕"); // Reset nút kia
            applyFilterAndSort();
        });

        sortTimeHeader.setOnMouseClicked(e -> {
            isSortTimeAsc = !isSortTimeAsc;
            sortTimeHeader.setText("THỜI GIAN " + (isSortTimeAsc ? "↑" : "↓"));
            sortPriceHeader.setText("GIÁ TRẦN HIỆN TẠI ↕");
            applyFilterAndSort();
        });
    }

    private void applyFilterAndSort() {
        String keyword = searchField.getText().trim().toLowerCase();

        // 1. Lọc trên RAM
        displayedAuctions = allLiveAuctions.stream()
                .filter(a -> keyword.isEmpty() ||
                        a.getId().toLowerCase().contains(keyword) ||
                        a.getItem().getTitle().toLowerCase().contains(keyword))
                .collect(Collectors.toList());

        // 2. Sắp xếp trên RAM
        if (sortPriceHeader.getText().contains("↑") || sortPriceHeader.getText().contains("↓")) {
            displayedAuctions.sort((a1, a2) -> isSortPriceAsc
                    ? Long.compare(a1.getHighestBid(), a2.getHighestBid())
                    : Long.compare(a2.getHighestBid(), a1.getHighestBid()));
        } else if (sortTimeHeader.getText().contains("↑") || sortTimeHeader.getText().contains("↓")) {
            displayedAuctions.sort((a1, a2) -> isSortTimeAsc
                    ? a1.getEndTime().compareTo(a2.getEndTime())
                    : a2.getEndTime().compareTo(a1.getEndTime()));
        }

        // 3. Render lại danh sách
        renderTableBody();
    }

    private void renderTableBody() {
        tableBody.getChildren().clear();
        for (Auction auction : displayedAuctions) {
            // Lấy từ Cache ra nếu đã vẽ rồi, chưa có thì vẽ mới
            HBox row = rowCache.computeIfAbsent(auction.getId(), id -> createAuctionRow(auction));
            tableBody.getChildren().add(row);
        }
        pageInfoLabel.setText("Hiển thị " + displayedAuctions.size() + " phiên");
    }

    // ==========================================
    // 2. RENDER ĐỘNG DỮ LIỆU FXML
    // ==========================================
    private HBox createAuctionRow(Auction auction) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("auction-table-row");

        // 1. Mã phiên
        Label idLabel = new Label("#" + auction.getId());
        idLabel.setPrefWidth(145);
        idLabel.getStyleClass().add("auction-code-cell");

        // 2. Tên sản phẩm
        HBox productBox = new HBox();
        productBox.setAlignment(Pos.CENTER_LEFT);
        productBox.setPrefWidth(370);
        productBox.setSpacing(14);

        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("auction-product-thumb-dark"); // Có thể random màu tùy thích
        Label icon = new Label("📦"); // Tạm để icon hộp hàng
        icon.getStyleClass().add("auction-thumb-icon");
        icon.setStyle("-fx-text-fill: white;");
        thumb.getChildren().add(icon);

        Label nameLabel = new Label(auction.getItem().getTitle());
        nameLabel.getStyleClass().add("auction-product-name");
        nameLabel.setWrapText(true);
        productBox.getChildren().addAll(thumb, nameLabel);

        // 3. Giá trần
        Label priceLabel = new Label(vndFormat.format(auction.getHighestBid()) + " đ");
        priceLabel.setPrefWidth(230);
        priceLabel.getStyleClass().add("auction-money-cell");

        // 4. Người giữ Top 1
        VBox topBidderBox = new VBox();
        topBidderBox.setPrefWidth(220);
        topBidderBox.setSpacing(2);
        String bidderName = auction.getWinner() != null ? auction.getWinner().getFullName() : "Chưa có ai";
        String bidderId = auction.getWinner() != null ? "ID: " + auction.getWinner().getId() : "—";
        Label bidderNameLabel = new Label(bidderName);
        bidderNameLabel.getStyleClass().add("auction-bidder-name");
        Label bidderIdLabel = new Label(bidderId);
        bidderIdLabel.getStyleClass().add("auction-bidder-id");
        topBidderBox.getChildren().addAll(bidderNameLabel, bidderIdLabel);

        // 5. Thời gian (Gán vào Cache để lát update riêng)
        Label timeLabel = new Label("Đang tính...");
        timeLabel.setPrefWidth(150);
        timeLabel.getStyleClass().add("auction-time-normal");
        timeLabelCache.put(auction.getId(), timeLabel);

        // 6. Thao tác Hủy
        Button cancelBtn = new Button("⊘  Hủy phiên");
        cancelBtn.getStyleClass().add("auction-cancel-button");
        cancelBtn.setOnAction(e -> handleEmergencyCancel(auction.getId()));

        row.getChildren().addAll(idLabel, productBox, priceLabel, topBidderBox, timeLabel, cancelBtn);
        return row;
    }

    // ==========================================
    // 3. THAO TÁC HỦY KHẨN CẤP
    // ==========================================
    private void handleEmergencyCancel(String auctionId) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cảnh báo khẩn cấp");
        alert.setHeaderText("HỦY PHIÊN ĐẤU GIÁ");
        alert.setContentText("Bạn có chắc chắn muốn hủy khẩn cấp phiên đấu giá #" + auctionId + " không?\nHành động này không thể hoàn tác và hệ thống sẽ tự động hoàn tiền cọc.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (client != null) {
                client.sendToServer(new Message("FORCE_CANCEL_AUCTION", auctionId));
            }
        }
    }

    // ==========================================
    // 4. LẮNG NGHE REAL-TIME SOCKET
    // ==========================================
    @Override
    public void onLiveAuctionsReceived(List<Auction> liveAuctions) {
        Platform.runLater(() -> {
            allLiveAuctions.clear();
            if (liveAuctions != null) {
                allLiveAuctions.addAll(liveAuctions);
            }
            int count = allLiveAuctions.size();
            liveSessionsLabel.setText(String.valueOf(count));
            liveBadgeLabel.setText("● Live: " + count + " Sessions"); // Đồng bộ Badge xanh góc trái

            // Tự động tính Tổng doanh thu (Giá trần) từ DB
            long totalRev = allLiveAuctions.stream().mapToLong(Auction::getHighestBid).sum();
            totalRevenueLabel.setText(vndFormat.format(totalRev) + " đ");

            applyFilterAndSort();
        });
    }

    // Bắt tín hiệu Nhịp đập mỗi giây từ Server
    @Override
    public void onServerTick(String currentServerTimeStr) {
        Platform.runLater(() -> {
            LocalDateTime now = LocalDateTime.now(); // Hoặc parse thời gian Server gửi về

            for (Auction auction : displayedAuctions) {
                Label timeLabel = timeLabelCache.get(auction.getId());
                if (timeLabel == null) continue;

                Duration duration = Duration.between(now, auction.getEndTime());
                long secondsLeft = duration.getSeconds();

                if (secondsLeft <= 0) {
                    timeLabel.setText("ĐÃ KẾT THÚC");
                    timeLabel.getStyleClass().setAll("auction-time-danger");
                }
                // NẾU CÒN HƠN 24 GIỜ (86400 giây) -> HIỂN THỊ NGÀY + GIỜ
                else if (secondsLeft > 86400) {
                    timeLabel.setText("📅 " + auction.getEndTime().format(dateFormatter));
                    timeLabel.getStyleClass().setAll("auction-time-normal");
                }
                // NẾU CÒN DƯỚI 24 GIỜ -> HIỂN THỊ ĐẾM NGƯỢC
                else {
                    long h = secondsLeft / 3600;
                    long m = (secondsLeft % 3600) / 60;
                    long s = secondsLeft % 60;
                    timeLabel.setText(String.format("⏱ %02d:%02d:%02d", h, m, s));

                    if (secondsLeft < 60) {
                        if (!timeLabel.getStyleClass().contains("auction-time-danger"))
                            timeLabel.getStyleClass().setAll("auction-time-danger");
                    } else {
                        if (!timeLabel.getStyleClass().contains("auction-time-normal"))
                            timeLabel.getStyleClass().setAll("auction-time-normal");
                    }
                }
            }
        });
    }
    // 3. REAL-TIME NHẢY SỐ LƯỢT ĐẶT GIÁ & DOANH THU
    // ==========================================
    // Hàm này sẽ tự động bắt sóng mỗi khi có ai đó trên sàn đặt giá thành công
    @Override
    public void onHighestBidUpdated(String auctionId, long newAmount, String winnerName) {
        Platform.runLater(() -> {
            // Nhảy số lượt đặt giá
            totalBidsCounter++;
            totalBidsLabel.setText(vndFormat.format(totalBidsCounter));

            // Tìm phiên vừa được đặt giá trên RAM và cập nhật lại giá tiền + Tên người dẫn đầu
            for (Auction a : allLiveAuctions) {
                if (a.getId().equals(auctionId)) {
                    a.setHighestBid(newAmount);
                    // Cập nhật lại tổng doanh thu
                    if (a.getWinner() == null) {
                        com.nhomX.example.model.RegularUser tempWinner = new com.nhomX.example.model.RegularUser();
                        tempWinner.setFullName(winnerName);
                        a.setWinner(tempWinner);
                    } else {
                        a.getWinner().setFullName(winnerName);
                    }
                    long newTotalRev = allLiveAuctions.stream().mapToLong(Auction::getHighestBid).sum();
                    totalRevenueLabel.setText(vndFormat.format(newTotalRev) + " đ");
                    // 🚨 4. BẮT BUỘC: Xóa dòng cũ khỏi Cache để ép giao diện vẽ lại giá mới
                    rowCache.remove(auctionId);

                    // Render lại dòng của phiên đó (để cập nhật tên top 1 và giá trên bảng)
                    applyFilterAndSort();
                    break;
                }
            }
        });
    }
}

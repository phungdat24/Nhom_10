package com.nhomX.example.controller.client;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class LiveAuctionController extends BaseController
        implements Initializable, ServerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(LiveAuctionController.class);
    // Nơi chứa các thẻ sản phẩm. Hãy đảm bảo trong file LiveAuction.fxml bạn có một FlowPane với
    // fx:id này
    @FXML
    private FlowPane liveAuctionContainer;
    @FXML
    private TextField txtSearch; // Đã liên kết với fx:id trong FXML
    @FXML
    private Label lblItem; // Đã liên kết với fx:id trong FXML
    // Ổ LƯU TRỮ TRÊN RAM CỦA CLIENT
    private final List<Auction> originalAuctions = new ArrayList<>();

    // Cấu trúc dữ liệu để theo dõi các thẻ sản phẩm đang hiển thị
    // Giúp tìm và cập nhật giá realtime với độ trễ thấp nhất O(1)
    private Map<String, ItemCardController> activeAuctionCards = new HashMap<>();

    // BIẾN QUẢN LÝ TIÊU CHÍ LỌC HIỆN TẠI
    private String selectedCategory = "TẤT CẢ";
    private String selectedSortOrder = "MẶC ĐỊNH"; // "MẶC ĐỊNH", "GIÁ_TĂNG", "GIÁ_GIẢM",
                                                   // "SẮP_HẾT_GIỜ"

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Gọi hàm cập nhật Header từ BaseController
        updateHeaderUI();
        // TÁC VỤ: Thiết lập bộ lắng nghe gõ phím Real-time cho ô Tìm kiếm
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                // Cứ người dùng gõ thêm hoặc xóa ký tự -> Tự động chạy bộ lọc cộng dồn
                applyCompoundFilter();
            });
        }

        // 2. Thiết lập kết nối mạng và giành quyền lắng nghe sự kiện
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);

            // 3. Gửi yêu cầu lấy danh sách lên Server
            client.sendToServer(new Message("GET_ALL_AUCTIONS", null));
            logger.info("LIVE AUCTION: Đã gửi yêu cầu lấy danh sách phiên đấu giá.");
        } else {
            logger.error("LIVE AUCTION: Lỗi! Không tìm thấy kết nối Socket.");
        }
    }

    // THUẬT TOÁN LỌC CỘNG DỒN SỬ DỤNG JAVA STREAM API (CORE LOGIC)
    // =========================================================================
    private void applyCompoundFilter() {
        String searchKeyword = (txtSearch != null) ? txtSearch.getText().trim().toLowerCase() : "";

        // Bước 1: Lọc dữ liệu bằng Stream
        List<Auction> filteredList = originalAuctions.stream().filter(auction -> {
            // 1.1 Lọc theo từ khóa Tìm kiếm (Khớp tên sản phẩm )
            String title = auction.getItem().getTitle().toLowerCase();
            return title.contains(searchKeyword);
        }).filter(auction -> {
            // 1.2 Lọc theo Danh mục sản phẩm (Category)
            if ("TẤT CẢ".equals(selectedCategory))
                return true;
            return selectedCategory.equalsIgnoreCase(auction.getItem().getCategory());
        }).collect(Collectors.toList());

        // Bước 2: Sắp xếp dữ liệu (Sorting)
        switch (selectedSortOrder) {
            case "GIÁ_TĂNG":
                filteredList.sort(Comparator.comparingLong(Auction::getHighestBid));
                break;
            case "GIÁ_GIẢM":
                filteredList.sort((a1, a2) -> Long.compare(a2.getHighestBid(), a1.getHighestBid()));
                break;
            case "SẮP_HẾT_GIỜ":
                filteredList.sort(Comparator.comparing(Auction::getEndTime));
                break;
            default:
                // "MẶC ĐỊNH": Giữ nguyên thứ tự Server trả về
                break;
        }

        // Bước 3: Đổ dữ liệu đã lọc sạch ra màn hình
        renderAuctionsToUI(filteredList);
    }

    // HÀM VẼ THẺ CARD LÊN GIAO DIỆN HÌNH THỂ
    // =========================================================================
    private void renderAuctionsToUI(List<Auction> auctions) {
        liveAuctionContainer.getChildren().clear();
        activeAuctionCards.clear();

        // Cập nhật nhãn số lượng sản phẩm (Ví dụ: "All item(12)")
        if (lblItem != null) {
            lblItem.setText(String.format("Sản phẩm tìm thấy (%d)", auctions.size()));
        }

        for (Auction auction : auctions) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/nhomX/example/fxml/client/Itemcard.fxml"));
                VBox cardItem = loader.load();

                ItemCardController cardController = loader.getController();
                cardController.setAuctionData(auction);

                // Lưu vào Map phục vụ cập nhật giá nhảy số Real-time O(1)
                activeAuctionCards.put(auction.getId(), cardController);
                liveAuctionContainer.getChildren().add(cardItem);
            } catch (IOException e) {
                logger.error("Không thể load giao diện thẻ sản phẩm", e);
            }
        }
    }

    // XỬ LÝ SỰ KIỆN CHỌN DANH MỤC (CATEGORY MOUSE POPUP)
    // =========================================================================
    @FXML
    public void handleCategory(ActionEvent event) {
        Button btn = (Button) event.getSource();
        ContextMenu categoryMenu = new ContextMenu();

        // Tạo danh sách các danh mục theo đúng ENUM trong DB của em
        String[] categories = {"Tất cả", "ELECTRONICS", "JEWELRY", "ART", "GENERALITEM"};

        for (String cat : categories) {
            MenuItem item = new MenuItem(cat);
            item.setOnAction(e -> {
                this.selectedCategory = cat.toUpperCase();
                btn.setText("🛒 " + cat); // Đổi text trên nút để báo hiệu cho người dùng biết họ
                                          // đang chọn gì
                applyCompoundFilter(); // Chạy lại bộ lọc
            });
            categoryMenu.getItems().add(item);
        }

        // Hiển thị menu ngay dưới chân nút bấm
        categoryMenu.show(btn,
                btn.getScene().getWindow().getX()
                        + btn.localToScene(btn.getBoundsInLocal()).getMinX(),
                btn.getScene().getWindow().getY()
                        + btn.localToScene(btn.getBoundsInLocal()).getMaxY() + 30);
    }

    // XỬ LÝ SỰ KIỆN CHỌN BỘ LỌC SẮP XẾP (FILTER MOUSE POPUP)
    @FXML
    public void handleFilter(ActionEvent event) {
        Button btn = (Button) event.getSource();
        ContextMenu filterMenu = new ContextMenu();

        MenuItem m1 = new MenuItem("Mặc định");
        m1.setOnAction(e -> {
            this.selectedSortOrder = "MẶC ĐỊNH";
            btn.setText("⏳ Mặc định");
            applyCompoundFilter();
        });

        MenuItem m2 = new MenuItem("Giá: Thấp đến Cao");
        m2.setOnAction(e -> {
            this.selectedSortOrder = "GIÁ_TĂNG";
            btn.setText("📉 Giá tăng dần");
            applyCompoundFilter();
        });

        MenuItem m3 = new MenuItem("Giá: Cao đến Thấp");
        m3.setOnAction(e -> {
            this.selectedSortOrder = "GIÁ_GIẢM";
            btn.setText("📈 Giá giảm dần");
            applyCompoundFilter();
        });

        MenuItem m4 = new MenuItem("Thời gian: Sắp kết thúc");
        m4.setOnAction(e -> {
            this.selectedSortOrder = "SẮP_HẾT_GIỜ";
            btn.setText("⏱ Sắp hết hạn");
            applyCompoundFilter();
        });

        filterMenu.getItems().addAll(m1, m2, m3, m4);
        filterMenu.show(btn,
                btn.getScene().getWindow().getX()
                        + btn.localToScene(btn.getBoundsInLocal()).getMinX(),
                btn.getScene().getWindow().getY()
                        + btn.localToScene(btn.getBoundsInLocal()).getMaxY() + 30);
    }

    @Override
    public void onAuctionsReceived(List<Auction> auctions) {
        // Luôn bọc trong Platform.runLater khi update UI từ luồng Socket
        Platform.runLater(() -> {
            liveAuctionContainer.getChildren().clear();
            activeAuctionCards.clear();
            originalAuctions.clear();
            originalAuctions.addAll(AuctionManager.getInstance().getActiveAuctions());
            // Vẽ bằng bộ lọc
            applyCompoundFilter();
        });
    }

    @Override
    public void onHighestBidUpdated(String itemId, long newPrice, String bidderName) {
        // Nhận tín hiệu giá mới từ Server
        Platform.runLater(() -> {
            // 1. Cập nhật giá tươi vào kho dữ liệu gốc RAM trước để nhỡ người dùng đang lọc không
            // bị mất giá mới
            for (Auction a : originalAuctions) {
                if (a.getId().equals(itemId)) {
                    a.setHighestBid(newPrice);
                    break;
                }
            }
            ItemCardController card = activeAuctionCards.get(itemId);
            if (card != null) {
                // TÌM KIẾM THỜI GIAN MỚI TỪ CACHE (AUCTION MANAGER)
                // ========================================================
                LocalDateTime newEndTime = null;
                Auction cachedAuction = AuctionManager.getInstance().getAuctionById(itemId);
                if (cachedAuction != null) {
                    newEndTime = cachedAuction.getEndTime();
                }
                // Gọi hàm cập nhật giá đơn lẻ trên thẻ đó
                card.updateRealtimePrice(newPrice, newEndTime);
                logger.info("LIVE AUCTION: Đã nhảy giá mới {} cho món {}", newPrice, itemId);
            }
        });
    }

}

package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SellerController extends BaseController implements Initializable, ServerEventListener {
    @FXML
    private Label lblRevenue;
    @FXML
    private FlowPane contentArea;

    // Khai báo 3 nút Tab để đổi màu
    @FXML
    private Button btnPending;
    @FXML
    private Button btnActive;
    @FXML
    private Button btnSold;

    // Kho chứa dữ liệu trên RAM (Single Page Application Approach)
    private List<Auction> allMySellerItems = new ArrayList<>();

    // Trạng thái hiện tại ("PENDING", "ACTIVE", "SOLD")
    private String currentFilterStatus = "ACTIVE";

    // Nút đang chọn: Nền vàng, chữ trắng, viền vàng (để đồng bộ)
    private final String STYLE_ACTIVE = "-fx-background-color: #c9a227; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8; -fx-border-color: #c9a227; -fx-border-radius: 8; -fx-border-width: 1;";

    // Nút không chọn: Nền trong suốt, chữ xám, viền xám nhạt
    private final String STYLE_INACTIVE = "-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8; -fx-border-color: #cccccc; -fx-border-radius: 8; -fx-border-width: 1;";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cập nhật thông tin Header (Từ BaseController)
        updateHeaderUI();

        // Mặc định chọn Tab "Đang đấu giá"
        switchTab("TAB_ACTIVE", btnActive);

        // Gửi yêu cầu lấy danh sách tài sản của Seller này lên Server
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);
            String myUserId = SessionManager.getInstance().getCurrentUser().getId();
            client.sendToServer(new Message("GET_SELLER_AUCTIONS", myUserId));
        }
    }
    // LOGIC CHUYỂN TAB VÀ LỌC DỮ LIỆU

    @FXML
    private void btnTabPending(ActionEvent event) {
        switchTab("TAB_PENDING", btnPending);
    }

    @FXML
    private void btnTabActive(ActionEvent event) {
        switchTab("TAB_ACTIVE", btnActive);
    }

    @FXML
    private void btnTabSold(ActionEvent event) {
        switchTab("TAB_SOLD", btnSold); // Chú ý: CSDL có thể lưu là CLOSED
    }

    private void switchTab(String targetStatus, Button activeBtn) {
        this.currentFilterStatus = targetStatus;

        // Reset màu tất cả các nút về màu xám
        btnPending.setStyle(STYLE_INACTIVE);
        btnActive.setStyle(STYLE_INACTIVE);
        btnSold.setStyle(STYLE_INACTIVE);

        // Nhuộm vàng nút đang được chọn
        activeBtn.setStyle(STYLE_ACTIVE);

        // Lọc và vẽ lại màn hình
        renderFilteredAuctions();
    }
    // LOGIC HIỂN THỊ & TÍNH TOÁN DOANH THU

    private void renderFilteredAuctions() {
        contentArea.getChildren().clear();
        long projectedRevenue = 0;

        for (Auction item : allMySellerItems) {
            // TÍNH TOÁN DOANH THU THỜI GIAN THỰC
            // (Tính tiền những món Đang đấu giá có người đặt, hoặc Đã bán thành công)
            // 1. RÚT XUẤT ENUM THÀNH STRING ĐỂ SO SÁNH
            // Sử dụng hàm .name() của Java Enum để biến AuctionStatus.ACTIVE thành chuỗi "ACTIVE"
            String statusName = item.getStatus().name();

            if (statusName.equals("RUNNING") || statusName.equals("FINISHED") || statusName.equals("PAID")) {
                projectedRevenue += item.getHighestBid();
            }

            // 2. KỸ THUẬT ÁNH XẠ NHÓM (GROUP MAPPING)
            boolean isMatchTab = false;

            switch (currentFilterStatus) {
                case "TAB_PENDING":
                    // Tab "Chờ lên sàn" -> Khớp với PENDING
                    isMatchTab = statusName.equals("PENDING") ;;
                    break;

                case "TAB_ACTIVE":
                    // Tab "Đang đấu giá" -> Gom cả phiên vừa mở (OPEN) và phiên đang giành giật (RUNNING)
                    isMatchTab = statusName.equals("RUNNING")|| statusName.equals("OPEN");
                    break;

                case "TAB_SOLD":
                    // Tab "Đã bán" -> Gom cả phiên chốt sổ (FINISHED) và phiên đã nhận tiền (PAID)
                    isMatchTab = statusName.equals("FINISHED")
                            || statusName.equals("PAID")
                            || statusName.equals("CANCELED");;
                    break;
            }

            // 3. VẼ LÊN MÀN HÌNH NẾU KHỚP VỚI TAB ĐANG MỞ
            if (isMatchTab) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/SellerItemCard.fxml"));
                    VBox card = loader.load();

                    // TODO: Mở comment 2 dòng dưới sau khi tạo xong Controller
                    SellerItemCardController cardController = loader.getController();
                    cardController.setData(item);

                    contentArea.getChildren().add(card);
                } catch (IOException e) {
                    System.err.println("Lỗi load thẻ sản phẩm của Seller: " + e.getMessage());
                }
            }
        }

        // Cập nhật nhãn doanh thu lên UI
        lblRevenue.setText(CurrencyFormatter.formatVND(projectedRevenue));
    }
    @Override
    public void onSellerAuctionsReceived(List<Auction> sellerAuctions) {
        // Platform.runLater để đảm bảo việc vẽ UI diễn ra trên luồng chính (JavaFX Application Thread)
        Platform.runLater(() -> {
            // Cập nhật lại kho dữ liệu trên RAM
            this.allMySellerItems = sellerAuctions;

            // Gọi lại hàm vẽ màn hình để hiển thị các món hàng
            renderFilteredAuctions();
            System.out.println("SELLER: Đã tải xong " + sellerAuctions.size() + " sản phẩm.");
        });
    }

    // TÍNH NĂNG TẠO MỚI SẢN PHẨM

    @FXML
    private void handleAddItem(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/AddItemcard.fxml"));
            Parent root = loader.load();

            // TẠO MỘT STAGE MỚI (CỬA SỔ MỚI) CHỈ DÀNH CHO POP-UP
            Stage popupStage = new Stage();
            popupStage.setTitle("Thêm Sản Phẩm Mới");
            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);

            // Lệnh này quan trọng nhất: KHÓA CỨNG CỬA SỔ CHA (Trang Seller)
            popupStage.initModality(Modality.WINDOW_MODAL);

            // Lấy cửa sổ cha để làm "nạn nhân" bị khóa
            Stage parentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            popupStage.initOwner(parentStage);

            // Hiển thị Pop-up và chờ đợi (Code sẽ dừng ở đây cho đến khi Pop-up đóng)
            popupStage.showAndWait();

            // SAU KHI POP-UP ĐÓNG LẠI, HÃY CẬP NHẬT LẠI BẢNG DANH SÁCH Ở ĐÂY
            System.out.println("Pop-up đã đóng. Đang tải lại danh sách sản phẩm...");
            renderFilteredAuctions(); // Hàm tải lại dữ liệu của em

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onHighestBidUpdated(String auctionId, long newPrice, String bidderName) {
        Platform.runLater(() -> {
            for (Auction item : allMySellerItems) {
                if (item.getId().equals(auctionId)) {
                    // 1. Cập nhật giá trần mới nhất
                    item.setHighestBid(newPrice);

                    // 2. Nếu phiên đang ở trạng thái OPEN (Sắp lên sàn) mà có người bid,
                    // tự động chuyển trạng thái mô hình sang RUNNING
                    if (item.getStatus() == AuctionStatus.OPEN) {
                        item.setStatus(AuctionStatus.RUNNING);
                    }

                    // 3. Vẽ lại màn hình để cập nhật cả thẻ lẫn Tổng doanh thu dự kiến
                    renderFilteredAuctions();
                    System.out.println("🔄 SELLER REAL-TIME: Phiên " + auctionId + " vừa nảy giá lên " + newPrice);
                    break;
                }
            }
        });
    }
    // ==========================================
    // LẮNG NGHE SỰ KIỆN ĐÓNG PHIÊN ĐẤU GIÁ
    // ==========================================
    @Override
    public void onAuctionClosed(String auctionId, String winnerId) {
        Platform.runLater(() -> {
            for (Auction item : allMySellerItems) {
                if (item.getId().equals(auctionId)) {
                    // Chuyển trạng thái mô hình sang FINISHED để nó tự động nhảy tab sang "Đã bán"
                    item.setStatus(AuctionStatus.FINISHED);
                    renderFilteredAuctions();
                    System.out.println("🏁 SELLER REAL-TIME: Phiên " + auctionId + " đã kết thúc!");
                    break;
                }
            }
        });
    }

}

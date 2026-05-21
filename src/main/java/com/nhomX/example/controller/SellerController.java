package com.nhomX.example.controller;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

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

    // Các hằng số Style cho Tab
    private final String STYLE_ACTIVE = "-fx-background-color: #c9a227; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8;";
    private final String STYLE_INACTIVE = "-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: #888; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8;"; // Tùy chỉnh màu xám cho khớp css của em

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cập nhật thông tin Header (Từ BaseController)
        updateHeaderUI();

        // Mặc định chọn Tab "Đang đấu giá"
        switchTab("ACTIVE", btnActive);

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
                    isMatchTab = statusName.equals("PENDING");
                    break;

                case "TAB_ACTIVE":
                    // Tab "Đang đấu giá" -> Gom cả phiên vừa mở (OPEN) và phiên đang giành giật (RUNNING)
                    isMatchTab = statusName.equals("OPEN") || statusName.equals("RUNNING");
                    break;

                case "TAB_SOLD":
                    // Tab "Đã bán" -> Gom cả phiên chốt sổ (FINISHED) và phiên đã nhận tiền (PAID)
                    isMatchTab = statusName.equals("FINISHED") || statusName.equals("PAID");
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


    // TÍNH NĂNG TẠO MỚI SẢN PHẨM

    @FXML
    private void handleAddItem(ActionEvent event) {
        System.out.println("Mở giao diện Đăng sản phẩm mới...");
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/AddItemcard.fxml");
    }
    // NHẬN DỮ LIỆU TỪ SERVER (OVERRIDE)
        // [Nâng cao]: Nếu em muốn khi đang đứng ở màn hình Seller mà giá món hàng nảy lên,
        // Em có thể chọc vào allMyItems để sửa giá, rồi gọi lại renderFilteredAuctions()

}

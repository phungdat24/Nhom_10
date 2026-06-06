package com.nhomX.example.controller.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.MyAuctionStatus;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

public class MyAuctionsController extends BaseController implements ServerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(MyAuctionsController.class);
    @FXML
    private Label lblTotalJoined;
    @FXML
    private Label lblTotalLockedMoney;
    @FXML
    private FlowPane contentArea;
    // KHAI BÁO 3 NÚT TAB TỪ FXML ĐỂ ĐIỀU KHIỂN ĐỔI MÀU GIAO DIỆN
    @FXML
    private Button btnTabActive;
    @FXML
    private Button btnTabWon;
    @FXML
    private Button btnTabLost;
    // Kho chứa dữ liệu thô tải từ Server về để lọc trên RAM
    private List<MyAuctionDTO> rawMyAuctionsList = new ArrayList<>();
    // Bộ lọc Tab hiện tại (Mặc định khi mở màn hình là "TAB_ACTIVE")
    private String currentFilterTab = "TAB_ACTIVE";

    // Các hằng số Style CSS cho Tab (Em có thể tinh chỉnh màu cho đồng bộ với css của dự án)
    private final String STYLE_ACTIVE =
            "-fx-background-color: #c9a227; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 15; -fx-background-radius: 6;";
    private final String STYLE_INACTIVE =
            "-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: #888; -fx-font-weight: bold; -fx-padding: 6 15; -fx-background-radius: 6;";

    @FXML
    public void initialize() {
        updateHeaderUI();
        // Thiết lập trạng thái Tab sáng/tối ban đầu cho nút "Đang diễn ra"
        resetTabStyles();
        btnTabActive.setStyle(STYLE_ACTIVE);

        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);
            fetchMyAuctionsData(client);
        }
    }

    // Hàm gọi API tách riêng để tái sử dụng
    private void fetchMyAuctionsData(AuctionClient client) {
        if (SessionManager.getInstance().getCurrentUser() != null) {
            String userId = SessionManager.getInstance().getCurrentUser().getId();
            client.sendToServer(new Message("GET_MY_AUCTIONS", userId));
        }
    }

    @FXML
    private void handleTabActive(ActionEvent event) {
        switchTab("TAB_ACTIVE", btnTabActive);
    }

    @FXML
    private void handleTabWon(ActionEvent event) {
        switchTab("TAB_WON", btnTabWon);
    }

    @FXML
    private void handleTabLost(ActionEvent event) {
        switchTab("TAB_LOST", btnTabLost);
    }

    private void switchTab(String targetTab, Button clickedButton) {
        this.currentFilterTab = targetTab;

        // Reset toàn bộ nút về màu xám mờ
        resetTabStyles();

        // Nhuộm vàng nút vừa được click
        clickedButton.setStyle(STYLE_ACTIVE);

        // Tiến hành lọc dữ liệu và vẽ lại danh sách thẻ
        renderFilteredAuctions();
    }

    private void resetTabStyles() {
        if (btnTabActive != null)
            btnTabActive.setStyle(STYLE_INACTIVE);
        if (btnTabWon != null)
            btnTabWon.setStyle(STYLE_INACTIVE);
        if (btnTabLost != null)
            btnTabLost.setStyle(STYLE_INACTIVE);
    }

    @Override
    public void onMyAuctionsReceived(List<MyAuctionDTO> myAuctionsList) {
        Platform.runLater(() -> {
            // Đồng bộ dữ liệu vào kho chứa RAM
            this.rawMyAuctionsList.clear();
            if (myAuctionsList == null)
                return;
            // ✅ BỔ SUNG DÒNG NÀY: Đổ dữ liệu tươi từ Server vào kho chứa trên RAM để chuẩn bị lọc
            this.rawMyAuctionsList = myAuctionsList;
            // 1. Tính toán Thống kê Header
            int totalJoined = 0;
            long totalLockedMoney = 0;

            for (MyAuctionDTO dto : myAuctionsList) {
                // Chỉ đếm những phiên đang diễn ra
                if (dto.getMyStatus() == MyAuctionStatus.LEADING
                        || dto.getMyStatus() == MyAuctionStatus.OUTBID) {
                    totalJoined++;
                }
                if (dto.getMyStatus() == MyAuctionStatus.LEADING) {
                    // Tiền đang cược (tiền bị giam) thường là số tiền cao nhất mình đã đặt
                    totalLockedMoney += dto.getMyHighestBid();
                }
            }

            lblTotalJoined.setText("Đang tham gia: " + totalJoined);
            lblTotalLockedMoney.setText(
                    "Tổng tiền đang cược: " + CurrencyFormatter.formatVND(totalLockedMoney));

            // 3. Thực thi lọc và hiển thị danh sách theo Tab hiện tại
            renderFilteredAuctions();
        });
    }

    private void renderFilteredAuctions() {
        contentArea.getChildren().clear();

        for (MyAuctionDTO dto : rawMyAuctionsList) {
            boolean isMatchTab = false;
            MyAuctionStatus status = dto.getMyStatus();

            // Thực hiện State Machine ánh xạ dữ liệu DTO vào đúng Tab hình thể
            switch (currentFilterTab) {
                case "TAB_ACTIVE":
                    isMatchTab =
                            (status == MyAuctionStatus.LEADING || status == MyAuctionStatus.OUTBID);
                    break;
                case "TAB_WON":
                    isMatchTab = (status == MyAuctionStatus.WON);
                    break;
                case "TAB_LOST":
                    isMatchTab = (status == MyAuctionStatus.LOST);
                    break;
            }

            // Nếu phần tử khớp bộ lọc, tiến hành nạp FXML và đẩy lên giao diện
            if (isMatchTab) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass()
                            .getResource("/com/nhomX/example/fxml/client/MyAuctionCard.fxml"));
                    Node cardNode = loader.load();

                    MyAuctionCardController cardController = loader.getController();
                    cardController.setData(dto);

                    contentArea.getChildren().add(cardNode);
                } catch (Exception e) {
                    logger.error("Lỗi render MyAuctionCard tại tab {}", currentFilterTab, e);
                }
            }
        }
    }

    // Lắng nghe Real-time để cập nhật khi bị vượt giá
    @Override
    public void onHighestBidUpdated(String itemId, long newPrice, String bidderName) {
        // Chỉ gửi Request lên Server nếu món vừa có biến động thuộc về danh sách của mình
        boolean isMyItem =
                rawMyAuctionsList.stream().anyMatch(dto -> dto.getAuction().getId().equals(itemId));

        if (isMyItem) {
            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client != null) {
                fetchMyAuctionsData(client);
                logger.info(
                        "MY AUCTIONS: Phát hiện biến động ở món của mình, đang làm mới danh sách...");
            }
        }
    }

}

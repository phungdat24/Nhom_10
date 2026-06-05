package com.nhomX.example.controller.admin;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

public class AdminProductManagementController implements Initializable, ServerEventListener {
    private static final Logger logger =
            LoggerFactory.getLogger(AdminProductManagementController.class);
    // Ánh xạ lưới chứa sản phẩm
    @FXML
    private FlowPane flowPaneContainer;
    private final Map<String, Node> cardNodeMap = new HashMap<>();
    private final Map<String, AdminPendingProductCardController> cardControllerMap =
            new HashMap<>();

    // [REFACTOR] Label Empty State — hiển thị khi danh sách rỗng
    private Label lblEmptyState;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // 1. Giành quyền lắng nghe luồng Socket (Ống nghe)
            client.setServerEventListener(this);

            // 2. Chủ động yêu cầu Server ném về danh sách chờ duyệt
            // Lưu ý: Nếu ở file AuctionClient em chưa viết hàm requestPendingAuctions(),
            // em có thể gọi trực tiếp Message như dòng dưới:
            client.sendToServer(
                    new Message("GET_PENDING_AUCTIONS", client.getUsername(), null, 0, null));
        }
    }

    // ==========================================
    // KHU VỰC LẮNG NGHE PHẢN HỒI TỪ SERVER
    // ==========================================

    @Override
    public void onPendingAuctionsReceived(List<Auction> pendingAuctions) {
        Platform.runLater(() -> {
            // Dọn sạch lưới trước khi đổ dữ liệu mới (Chống bị đúp thẻ)
            flowPaneContainer.getChildren().clear();

            for (Auction auction : pendingAuctions) {
                addCardToFlowPane(auction);
            }
            logger.info("ADMIN: Đã tải {} sản phẩm chờ duyệt lên màn hình.",
                    pendingAuctions.size());
        });
    }

    @Override
    public void onAdminActionCompleted(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            if (isSuccess) {
                logger.info("ADMIN THÀNH CÔNG: {}", message);
                // Khi duyệt/từ chối thành công, cách an toàn và nhàn nhất là
                // tải lại toàn bộ danh sách từ Server để UI đồng bộ 100% với Database.
                refreshData();
            } else {
                logger.error("ADMIN THẤT BẠI: {}", message);
            }
        });
    }

    @Override
    public void onNewPendingAuctionReceived(Auction newAuction) {
        Platform.runLater(() -> {
            // SỰ MÀU NHIỆM CỦA REAL-TIME:
            // Chỉ cần gọi hàm add, thẻ sẽ tự động mọc ra trên màn hình Admin
            // ngay khoảnh khắc Seller vừa bấm nút "Đăng bán", không cần F5!
            addCardToFlowPane(newAuction);
            logger.info(
                    "ADMIN REAL-TIME: Phát hiện 1 sản phẩm mới vừa được đẩy lên sàn chờ duyệt!");
        });
    }

    // ==========================================
    // CÁC HÀM PHỤ TRỢ (HELPER METHODS)
    // ==========================================

    private void refreshData() {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.sendToServer(
                    new Message("GET_PENDING_AUCTIONS", client.getUsername(), null, 0, null));
        }
    }

    /**
     * Hàm dùng chung để load file FXML của "Thẻ sản phẩm", bơm dữ liệu, và nhét vào lưới FlowPane.
     */
    private void addCardToFlowPane(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass()
                    .getResource("/com/nhomX/example/fxml/admin/AdminPendingProductCard.fxml"));
            Node card = loader.load();

            // Rút cái Controller của thẻ đó ra để bơm dữ liệu (Data Binding)
            AdminPendingProductCardController cardController = loader.getController();
            cardController.setData(auction);

            // Thêm chiếc thẻ đã có hồn vào màn hình
            flowPaneContainer.getChildren().add(card);

        } catch (IOException e) {
            System.err.println("Lỗi khi render thẻ sản phẩm chờ duyệt: " + e.getMessage());
            e.printStackTrace(); // In ra chi tiết để dễ bắt bug
        }
    }
}

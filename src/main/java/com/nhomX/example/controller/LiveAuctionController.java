package com.nhomX.example.controller;

import com.nhomX.example.manager.AuctionManager;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class LiveAuctionController extends BaseController implements Initializable, ServerEventListener {
    // Nơi chứa các thẻ sản phẩm. Hãy đảm bảo trong file LiveAuction.fxml bạn có một FlowPane với fx:id này
    @FXML
    private FlowPane liveAuctionContainer;

    // Cấu trúc dữ liệu để theo dõi các thẻ sản phẩm đang hiển thị
    // Giúp tìm và cập nhật giá realtime với độ trễ thấp nhất O(1)
    private Map<String, ItemCardController> activeAuctionCards = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Gọi hàm cập nhật Header từ BaseController
        updateHeaderUI();

        // 2. Thiết lập kết nối mạng và giành quyền lắng nghe sự kiện
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);

            // 3. Gửi yêu cầu lấy danh sách lên Server
            client.sendToServer(new Message("GET_ALL_AUCTIONS", null));
            System.out.println("LIVE AUCTION: Đã gửi yêu cầu lấy danh sách phiên đấu giá.");
        } else {
            System.err.println("LIVE AUCTION: Lỗi! Không tìm thấy kết nối Socket.");
        }
    }

    @Override
    public void onAuctionsReceived(List<Auction> auctions) {
        // Luôn bọc trong Platform.runLater khi update UI từ luồng Socket
        Platform.runLater(() -> {
            liveAuctionContainer.getChildren().clear();
            activeAuctionCards.clear();
            List<Auction> activeAuctions = AuctionManager.getInstance().getActiveAuctions();

            for (Auction auction : activeAuctions) {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemCard.fxml"));
                        VBox cardItem = loader.load();

                        ItemCardController cardController = loader.getController();
                        cardController.setAuctionData(auction);

                        // Lưu CardController vào Map với Key là ID của phiên/sản phẩm
                        activeAuctionCards.put(auction.getId(), cardController);

                        liveAuctionContainer.getChildren().add(cardItem);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Không thể load giao diện thẻ sản phẩm.");
                    }
                }
            });
    }

    @Override
    public void onHighestBidUpdated(String itemId, long newPrice, String bidderName) {
        // Nhận tín hiệu giá mới từ Server
        Platform.runLater(() -> {
            ItemCardController card = activeAuctionCards.get(itemId);
            if (card != null) {
                // Gọi hàm cập nhật giá đơn lẻ trên thẻ đó
                card.updateRealtimePrice(newPrice);
                System.out.println("LIVE AUCTION: Đã nháy giá mới " + newPrice + " cho món " + itemId);
            }
        });
    }

    // Các hàm trống bắt buộc phải @Override từ ServerEventListener (nếu có)
    @Override
    public void onLoginResult(boolean isSuccess, String message, User userData) {}

    @Override
    public void onRegisterResult(boolean isSuccess, String message) {}
    @FXML
    public void handleCategory(ActionEvent event){

    }
    @FXML
    public void handleFilter(ActionEvent event){

    }
}

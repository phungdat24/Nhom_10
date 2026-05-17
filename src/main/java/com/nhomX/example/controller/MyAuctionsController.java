package com.nhomX.example.controller;

import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

import java.util.List;

public class MyAuctionsController extends BaseController implements ServerEventListener {
    @FXML
    private Label lblTotalJoined;
    @FXML
    private Label lblTotalLockedMoney;
    @FXML
    private FlowPane contentArea;

    @FXML
    public void initialize() {
        updateHeaderUI();

        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);
            // Gửi ID của user đang đăng nhập lên Server để lấy danh sách của riêng họ
            String userId = SessionManager.getInstance().getCurrentUser().getId();
            client.sendToServer(new Message("GET_MY_AUCTIONS", userId));
        }
    }

    @Override
    public void onMyAuctionsReceived(List<MyAuctionDTO> myAuctionsList) {
        Platform.runLater(() -> {
            if (myAuctionsList == null) return;

            // 1. Tính toán Thống kê Header
            int totalJoined = 0;
            long totalLockedMoney = 0;

            for (MyAuctionDTO dto : myAuctionsList) {
                // Chỉ đếm những phiên đang diễn ra
                if (dto.getMyStatus() == com.nhomX.example.model.MyAuctionStatus.LEADING ||
                        dto.getMyStatus() == com.nhomX.example.model.MyAuctionStatus.OUTBID) {
                    totalJoined++;
                    // Tiền đang cược (tiền bị giam) thường là số tiền cao nhất mình đã đặt
                    totalLockedMoney += dto.getMyHighestBid();
                }
            }

            lblTotalJoined.setText("Đang tham gia: " + totalJoined);
            lblTotalLockedMoney.setText("Tổng tiền đang cược: " + CurrencyFormatter.formatVND(totalLockedMoney));

            // 2. Render danh sách thẻ ra FlowPane
            contentArea.getChildren().clear();
            for (MyAuctionDTO dto : myAuctionsList) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/MyAuctionCard.fxml"));
                    Node cardNode = loader.load();

                    MyAuctionCardController cardController = loader.getController();
                    cardController.setData(dto);

                    contentArea.getChildren().add(cardNode);
                } catch (Exception e) {
                    System.err.println("Lỗi render MyAuctionCard: " + e.getMessage());
                }
            }
        });
    }

}

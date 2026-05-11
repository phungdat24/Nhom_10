package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.Items;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainDashBoardController extends BaseController implements Initializable, ServerEventListener {

    // ===== Header =====
    @FXML
    private Button btnLogin; // Nút "Đăng nhập" (hiện khi chưa login)
    @FXML
    private HBox userInfoBox; // HBox tên user (hiện khi đã login)
    @FXML
    private MenuButton menuUser;
    // ===== Sidebar =====
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnLiveAuction;
    @FXML
    private Button btnMyAuction;
    @FXML
    private Button btnSeller;
    @FXML
    private FlowPane contentArea;

    public static MainDashBoardController instance;

    @Override
    public void onAuctionsReceived(List<Auction> auctions) {
        // Xóa sạch dữ liệu cũ trên màn hình
        contentArea.getChildren().clear();
        // 2. Lặp qua từng món hàng và in ra màn hình
        for (Auction auction : auctions) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemCard.fxml"));
                VBox cardItem = loader.load(); // Load giao diện thẻ
                // Lấy controller của thẻ đó để nhồi dữ liệu
                ItemCardController cardController = loader.getController();
                cardController.setAuctionData(auction);;
                // Gắn thẻ vừa tạo vào màn hình chính
                contentArea.getChildren().add(cardItem);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private AuctionClient auctionClient;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // lưu lại bản thân khi update:
        instance = this;

        connectToAuctionServer();
        updateHeaderUI();

        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // Dành quyền:
            client.setServerEventListener(this);
            // Lấy item từ server
            client.sendToServer(new Message("GET_ALL_AUCTIONS", null));
            System.out.println("DASHBOARD: Đã gửi yêu cầu lấy danh sách Item.");
        }

    }

    public void connectToAuctionServer() {
        // nếu kết nối roi thì không tạo lại nữa
        if(SessionManager.getInstance().getAuctionClient() != null){
            this.auctionClient = SessionManager.getInstance().getAuctionClient();
            return;
        }
        String userName="Guest";
        if(SessionManager.getInstance().isLoggedIn()){
            userName = SessionManager.getInstance().getCurrentUser().getUserName();
        }
        this.auctionClient = new AuctionClient(userName);
        // Khởi tạo Client
        try {
            auctionClient.connect("localhost", 8080);
            System.out.println("Client: [" + userName + "] đã kết nối tới Server đấu giá!");
            // Cất vào kho cho các màn hình khác dùng chung
            SessionManager.getInstance().setAuctionClient(this.auctionClient);
        }catch (Exception e){
            System.err.println("Client: Lỗi kết nối Socket - " + e.getMessage());

        }
    }

    @Override
    public void onHighestBidUpdated(String itemId, long newPrice) {
        System.out.println("DASHBOARD BẮT SÓNG: Món hàng " + itemId + " vừa nhảy giá lên $" + newPrice);

        // Sau viết code quét danh sách ItemCard đang hiển thị
        // để update lại cái Label tiền trên màn hình ở đây
    }
    @FXML
    void handleFeaturedBid(ActionEvent event) {

    }
    @FXML
    void handleFeaturedDetail(ActionEvent event){

    }


}

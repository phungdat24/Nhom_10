package com.nhomX.example.controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class MainDashBoardController implements Initializable, ServerEventListener {

    // ===== Header =====
    @FXML
    private Button btnLogin; // Nút "Đăng nhập" (hiện khi chưa login)
    @FXML
    private HBox userInfoBox; // HBox tên user (hiện khi đã login)
    @FXML
    private Label lblUsername; // Label tên user bên trong userInfoBox

    // ===== Sidebar =====
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnLiveAuction;
    @FXML
    private Button btnMyAuction;
    @FXML
    private Button btnProfile;
    @FXML
    private Button btnSeller;
    @FXML
    private FlowPane contentArea;

    public static MainDashBoardController instance;

    @Override
    public void onItemsReceived(List<Items> items) {
        // Xóa sạch dữ liệu cũ trên màn hình
        contentArea.getChildren().clear();
        // 2. Lặp qua từng món hàng và in ra màn hình
        for (Items item : items) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/nhomX/example/fxml/ItemCard.fxml"));
                VBox cardItem = loader.load(); // Load giao diện thẻ
                // Lấy controller của thẻ đó để nhồi dữ liệu
                ItemCardController cardController = loader.getController();
                cardController.setItemData(item);
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
            client.sendToServer(new Message("GET_ALL_ITEMS", null));
            System.out.println("DASHBOARD: Đã gửi yêu cầu lấy danh sách Item.");
        }

    }

    public void connectToAuctionServer() {
        // nếu kết nối roi thì không tạo lại nữa
        if (SessionManager.getInstance().getAuctionClient() != null) {
            this.auctionClient = SessionManager.getInstance().getAuctionClient();
            return;
        }
        String userName = "Guest";
        if (SessionManager.getInstance().isLoggedIn()) {
            userName = SessionManager.getInstance().getCurrentUser().getUserName();
        }
        this.auctionClient = new AuctionClient(userName);
        // Khởi tạo Client
        try {
            auctionClient.connect("localhost", 8080);
            System.out.println("Client: [" + userName + "] đã kết nối tới Server đấu giá!");
            // Cất vào kho cho các màn hình khác dùng chung
            SessionManager.getInstance().setAuctionClient(this.auctionClient);
        } catch (Exception e) {
            System.err.println("Client: Lỗi kết nối Socket - " + e.getMessage());

        }
    }

    private void updateHeaderUI() {
        if (SessionManager.getInstance().isLoggedIn()) {
            // ---- Đã đăng nhập ----
            String name = SessionManager.getInstance().getCurrentUser().getFullName();
            lblUsername.setText("👤  " + name);

            // Ẩn nút Đăng nhập
            btnLogin.setVisible(false);
            btnLogin.setManaged(false);

            // Hiện HBox tên user
            userInfoBox.setVisible(true);
            userInfoBox.setManaged(true);

        } else {
            // ---- Chưa đăng nhập ----
            btnLogin.setVisible(true);
            btnLogin.setManaged(true);

            userInfoBox.setVisible(false);
            userInfoBox.setManaged(false);
        }
    }

    @FXML
    void handleLogin(ActionEvent event) {
        // Chuyển sang màn hình Login
        SceneSwitcher.switchScene(event, "/com/nhomX/example/fxml/login.fxml");
    }

    @FXML
    void handleLogout(ActionEvent event) {
        // Xóa session
        SessionManager.getInstance().logout();
        // Cập nhật lại header ngay lập tức
        updateHeaderUI();
        System.out.println("Đã đăng xuất!");
    }

    @FXML
    void handleDashboard(ActionEvent event) {
        System.out.println("Dashboard được nhấn");
    }

    @FXML
    void handleLiveAuction(ActionEvent event) {
        System.out.println("Live Auction được nhấn");
    }

    @FXML
    void handleMyAuctions(ActionEvent event) {
        System.out.println("My Auctions được nhấn");
    }

    @FXML
    void handleProfile(ActionEvent event) {
        System.out.println("Profile được nhấn");
    }

    @FXML
    void handleSeller(ActionEvent event) {
        System.out.println("Seller được nhấn");
    }

    @Override
    public void onPriceUpdated(String itemId, double newPrice) {
        System.out.println(
                "DASHBOARD BẮT SÓNG: Món hàng " + itemId + " vừa nhảy giá lên $" + newPrice);

        // Sau viết code quét danh sách ItemCard đang hiển thị
        // để update lại cái Label tiền trên màn hình ở đây
    }

    @FXML
    void handleFeaturedBid(ActionEvent event) {

    }

    @FXML
    void handleFeaturedDetail(ActionEvent event) {

    }

}

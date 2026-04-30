package com.nhomX.example.controller;

import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.ResourceBundle;

public class MainDashBoardController implements Initializable {

    // ===== Header =====
    @FXML private Button btnLogin;       // Nút "Đăng nhập" (hiện khi chưa login)
    @FXML private HBox   userInfoBox;    // HBox tên user   (hiện khi đã login)
    @FXML private Label  lblUsername;    // Label tên user bên trong userInfoBox

    // ===== Sidebar =====
    @FXML private Button btnDashboard;
    @FXML private Button btnLiveAuction;
    @FXML private Button btnMyAuction;
    @FXML private Button btnProfile;
    @FXML private Button btnSeller;

    private AuctionClient auctionClient;
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        updateHeaderUI();
        connectToAuctionServer();
    }
    public void connectToAuctionServer(){
        // nếu kết nối roi thì không tạo lại nữa
        if(auctionClient!= null){
            return;
        }
        String userName="User";
        if(SessionManager.getInstance().isLoggedIn()){
            userName = SessionManager.getInstance().getCurrentUser().getUserName();
        }
        auctionClient = new AuctionClient(userName);
        // Khởi tạo Client
        try {
            auctionClient.connect("localhost", 8080);
            System.out.println("Client: [" + userName + "] đã kết nối tới Server đấu giá!");
        }catch (Exception e){
            System.err.println("Client: Lỗi kết nối Socket - " + e.getMessage());

        }
    }

    // ============================================================
    // Cập nhật Header dựa trên trạng thái đăng nhập
    // ============================================================
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
        SceneSwitcher.switchScene(event,"/com/nhomX/example/fxml/login.fxml");
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

    @FXML
    void handleBid(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            // Nếu chưa đăng nhập → chuyển sang Login
            AlertUtils.showWarning("Yêu cầu đăng nhập", "Bạn cần đăng nhập tài khoản để tham gia đấu giá sản phẩm này!");
            handleLogin(event);
            return;
        }
        System.out.println("Đấu giá ngay được nhấn");
        if(auctionClient!= null){
            String testItemId = "SP_A1";
            double testAmount = 500000.0;

            // Gọi hàm của Mem 2 để bắn dữ liệu qua mạng
            auctionClient.placeBid(testItemId, testAmount);
            System.out.println("Client: Đã bắn dữ liệu (Item: " + testItemId + ", Giá: " + testAmount + ") lên Server.");
            AlertUtils.showSuccess("Đặt giá thành công", "Hệ thống đã ghi nhận mức giá của bạn!");
        } else {
            System.err.println("Client: Socket chưa được khởi tạo!");
            AlertUtils.showError("Lỗi hệ thống", "Mất kết nối tới Server. Vui lòng thử lại sau!");
        }
    }

    @FXML
    void handleBuy(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            handleLogin(event);
            return;
        }
        System.out.println("Mua ngay được nhấn");
    }
}

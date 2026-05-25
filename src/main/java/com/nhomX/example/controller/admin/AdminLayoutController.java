package com.nhomX.example.controller.admin;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class AdminLayoutController implements Initializable {
    // Đây là biến static Singleton để các trang con (như Quản lý SP)
    // có thể gọi ngược lại trang cha để đổi nội dung ở giữa.
    public static AdminLayoutController instance;

    // --- CÁC THÀNH PHẦN GIAO DIỆN TỪ FXML ---
    @FXML private Button btnHome;
    @FXML private Button btnUsers;
    @FXML
    private Button btnProducts;
    @FXML private Button btnAuctions;
    @FXML private Button btnSettings;

    @FXML private Label topbarTitle;
    @FXML private HBox searchBox;
    @FXML private TextField searchField;
    @FXML private StackPane contentArea;

    // Biến lưu trữ nút đang được chọn để làm hiệu ứng
    private Button currentActiveButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;

        // 1. Kiểm tra bảo mật cực đoan: Nếu không phải Admin thì đuổi cổ ra ngay
        if (!SessionManager.getInstance().isLoggedIn() ||
                !SessionManager.getInstance().getCurrentUser().getRoleName().equals("ADMIN")) {
            System.err.println("CẢNH BÁO BẢO MẬT: Xâm nhập trái phép không gian Admin!");
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
            return;
        }

        // 2. Mặc định load trang Quản lý sản phẩm khi vừa mở lên
        //handleNavProducts(null);
    }

    // --- CÁC HÀM XỬ LÝ CHUYỂN TRANG NỘI BỘ (ROUTING) ---

    @FXML
    void handleNavHome(ActionEvent event) {
        // topbarTitle.setText("Trang chủ");
        // setActiveButton(btnHome);
        // loadView("/com/nhomX/example/fxml/admin/AdminHome.fxml");
    }

    @FXML
    void handleNavUsers(ActionEvent event) {
        // topbarTitle.setText("Quản lý Người dùng");
        // setActiveButton(btnUsers);
        // loadView("/com/nhomX/example/fxml/admin/AdminUserManagement.fxml");
    }

    @FXML
    void handleNavProducts(ActionEvent event) {
        topbarTitle.setText("Quản lý sản phẩm");
        setActiveButton(btnProducts);
        // Em sẽ tạo file FXML này và Controller tương ứng sau
        loadView("/com/nhomX/example/fxml/admin/AdminProductManagement.fxml");
    }

    @FXML
    void handleNavAuctions(ActionEvent event) {
        // topbarTitle.setText("Quản lý Phiên đấu giá");
        // setActiveButton(btnAuctions);
        // loadView("/com/nhomX/example/fxml/admin/AdminAuctionManagement.fxml");
    }

    @FXML
    void handleNavSettings(ActionEvent event) {
        // Tích hợp Đăng xuất vào mục Cài đặt (hoặc tạo một nút Logout riêng)
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
    }

    @FXML
    void handleNotification(ActionEvent event) {
        System.out.println("Mở bảng thông báo Admin...");
    }

    // --- CÁC HÀM TIỆN ÍCH DÙNG CHUNG CHO KHÔNG GIAN ADMIN ---

    /**
     * Hàm lõi: Nhúng một giao diện FXML con vào vùng trống (contentArea) ở giữa màn hình
     */
    public void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();

            // Xóa nội dung cũ và nhét nội dung mới vào
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);

        } catch (IOException e) {
            System.err.println("LỖI ADMIN ROUTING: Không thể load file " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Đổi màu nút đang được chọn trên Sidebar
     */
    private void setActiveButton(Button newButton) {
        if (currentActiveButton != null) {
            currentActiveButton.getStyleClass().remove("active-nav");
        }
        if (newButton != null) {
            newButton.getStyleClass().add("active-nav");
            currentActiveButton = newButton;
        }
    }
}

package com.nhomX.example.controller.admin;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
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
import javafx.scene.layout.VBox;

public class AdminLayoutController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(AdminLayoutController.class);
    // Đây là biến static Singleton để các trang con (như Quản lý SP)
    // có thể gọi ngược lại trang cha để đổi nội dung ở giữa.
    public static AdminLayoutController instance;

    // --- CÁC THÀNH PHẦN GIAO DIỆN TỪ FXML ---
    @FXML
    private Button btnHome;
    @FXML
    private Button btnUsers;
    @FXML
    private Button btnProducts;
    @FXML
    private Button btnAuctions;
    @FXML
    private Button btnSettings;

    @FXML
    private Label topbarTitle;
    @FXML
    private HBox searchBox;
    @FXML
    private TextField searchField;
    @FXML
    private StackPane contentArea;
    @FXML
    private VBox adminLogoutPanel;
    // BIẾN LƯU TRỮ LỊCH SỬ TRANG (VIEW CACHING)
    // ==========================================
    private Node previousNode;
    private String previousTitle;

    // Biến lưu trữ nút đang được chọn để làm hiệu ứng
    private Button currentActiveButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;

        // 1. Kiểm tra bảo mật cực đoan: Nếu không phải Admin thì đuổi cổ ra ngay
        if (!SessionManager.getInstance().isLoggedIn()
                || !SessionManager.getInstance().getCurrentUser().getRoleName().equals("ADMIN")) {
            logger.warn("CẢNH BÁO BẢO MẬT: Xâm nhập trái phép không gian Admin");
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
            return;
        }

        // 2. Mặc định load trang Quản lý sản phẩm khi vừa mở lên
        handleNavProducts(null);
    }

    // --- CÁC HÀM XỬ LÝ CHUYỂN TRANG NỘI BỘ (ROUTING) ---

    @FXML
    void handleNavHome(ActionEvent event) {
        topbarTitle.setText("Trang chủ");
        setActiveButton(btnHome);
        loadView("/com/nhomX/example/fxml/admin/AdminDashboard.fxml");
    }

    @FXML
    void handleNavUsers(ActionEvent event) {
        topbarTitle.setText("Quản lý Người dùng");
        setActiveButton(btnUsers);
        loadView("/com/nhomX/example/fxml/admin/UserManagement.fxml");
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
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(null);
        }
        // Tích hợp Đăng xuất vào mục Cài đặt (hoặc tạo một nút Logout riêng)
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
    }

    @FXML
    void handleNotification(ActionEvent event) {
        logger.info("Mở bảng thông báo Admin...");
    }

    // --- CÁC HÀM TIỆN ÍCH DÙNG CHUNG CHO KHÔNG GIAN ADMIN ---

    /**
     * Hàm lõi: Nhúng một giao diện FXML con vào vùng trống (contentArea) ở giữa màn hình
     */
    public void loadView(String fxmlPath) {
        try {
            // getResource() trả về null nếu file không tồn tại
            var resource = getClass().getResource(fxmlPath);

            if (resource == null) {
                logger.error("ADMIN ROUTING ERROR: Không tìm thấy file FXML: {}", fxmlPath);
                logger.error("Kiểm tra file có tồn tại tại: src/main/resources{}", fxmlPath);
                return; // Dừng lại, không để crash toàn bộ app
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();
            // Xóa rỗng lịch sử cũ khi người dùng bấm sang một Tab chính khác
            this.previousNode = null;
            this.previousTitle = null;

            // Xóa nội dung cũ và nhét nội dung mới vào
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);

        } catch (IOException e) {
            logger.error("LỖI ADMIN ROUTING: Không thể load file {}", fxmlPath, e);
        }
    }

    /**
     * CẤT GIAO DIỆN CŨ ĐI VÀ HIỂN THỊ CHI TIẾT Được gọi bởi trang Danh sách khi Admin bấm nút "Xem
     * chi tiết"
     * 
     * @param newDetailNode Giao diện chi tiết đã được load sẵn
     * @param newTitle Tiêu đề mới cho Topbar
     */
    public void saveCurrentViewAndNavigate(Node newDetailNode, String newTitle) {
        // 1. Lưu lại giao diện hiện tại đang nằm trong contentArea
        if (!contentArea.getChildren().isEmpty()) {
            this.previousNode = contentArea.getChildren().get(0);
            this.previousTitle = topbarTitle.getText();
        }

        // 2. Chuyển sang màn hình chi tiết mới
        contentArea.getChildren().clear();
        contentArea.getChildren().add(newDetailNode);
        topbarTitle.setText(newTitle);
    }

    /**
     * KHÔI PHỤC LẠI GIAO DIỆN CŨ Được gọi bởi ItemDetailController khi Admin bấm "Quay lại"
     */
    public void restorePreviousView() {
        if (this.previousNode != null) {
            // Lấy giao diện đã cất từ trước ra gắn lại
            contentArea.getChildren().clear();
            contentArea.getChildren().add(this.previousNode);

            if (this.previousTitle != null) {
                topbarTitle.setText(this.previousTitle);
            }

            // Xóa cache để tránh lỗi chồng chéo
            this.previousNode = null;
            this.previousTitle = null;
        } else {
            // Safety fallback: Nếu lịch sử bị mất, tự động lùi về trang Quản lý sản phẩm gốc
            handleNavProducts(null);
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

    @FXML
    void toggleAdminMenu() {
        boolean showing = adminLogoutPanel.isVisible();
        adminLogoutPanel.setVisible(!showing);
        adminLogoutPanel.setManaged(!showing);
    }

    @FXML
    void handleLogout(ActionEvent event) {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(null);
        }

        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/client/login.fxml");
    }
}

package com.nhomX.example.controller.client;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.SceneSwitcher;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class MainDashBoardController extends BaseController implements Initializable {

    // ===== Sidebar =====
    @FXML
    private VBox sidebar;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnLiveAuction;
    @FXML
    private Button btnMyAuction;
    @FXML
    private Button btnSeller;
    @FXML
    private StackPane mainContentArea;

    public static MainDashBoardController instance;
    @FXML
    private List<Button> navButtons;
    @FXML
    private VBox sidebarUserArea;
    @FXML
    private VBox walletPanel;
    @FXML
    private HBox sidebarUserCard;
    @FXML
    private Label lblSidebarUserName;
    @FXML
    private Label lblCurrentBalance;
    @FXML
    private FontAwesomeIcon walletArrowIcon;

    private Node previousNode;

    private boolean isWalletOpen = false; // Biến cờ theo dõi trạng thái đóng/mở ví

    private AuctionClient auctionClient;
    private boolean isSidebarOpen = true;
    private static final double SIDEBAR_WIDTH = 220.0;


    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // lưu lại bản thân khi update:
        instance = this;
        updateHeaderUI();
        // Cài đặt Sidebar mặc định đóng
        if (btnDashboard != null) {
            navButtons = Arrays.asList(btnDashboard, btnLiveAuction, btnMyAuction, btnSeller);
            setActiveButton(btnDashboard);
            Platform.runLater(() -> {
                isSidebarOpen = false;
                sidebar.setMinWidth(0);
                sidebar.setPrefWidth(0);
                sidebar.setOpacity(0);
            });
        }
        // GỌI KIỂM TRA NGAY KHI VỪA MỞ APP LÊN
        checkUserLoginStatus();
        // Load trang mặc định
        loadView("/com/nhomX/example/fxml/client/DashboardContent.fxml");
    }

    // ===== SIDEBAR LOGIC =====
    @FXML
    protected void toggleSidebar(ActionEvent event) {
        if (sidebar == null)
            return;
        sidebar.setMinWidth(0);
        Timeline timeline = new Timeline();

        if (isSidebarOpen) {
            timeline.getKeyFrames()
                    .add(new KeyFrame(Duration.millis(250),
                            new KeyValue(sidebar.prefWidthProperty(), 0),
                            new KeyValue(sidebar.opacityProperty(), 0)));
        } else {
            timeline.getKeyFrames()
                    .add(new KeyFrame(Duration.millis(250),
                            new KeyValue(sidebar.prefWidthProperty(), SIDEBAR_WIDTH),
                            new KeyValue(sidebar.opacityProperty(), 1)));
        }
        timeline.play();
        isSidebarOpen = !isSidebarOpen;
    }

    public void closeSidebar() {
        if (!isSidebarOpen || sidebar == null)
            return;
        Timeline timeline = new Timeline();
        timeline.getKeyFrames()
                .add(new KeyFrame(Duration.millis(250),
                        new KeyValue(sidebar.prefWidthProperty(), 0),
                        new KeyValue(sidebar.opacityProperty(), 0)));
        timeline.setOnFinished(e -> isSidebarOpen = false);
        timeline.play();
    }

    // Cập nhật các nu trang thái trên giao diện:
    private void setActiveButton(Button activeBtn) {
        if (activeBtn == null)
            return;
        for (Button btn : navButtons) {
            // Xóa class active khỏi tất cả các nút
            btn.getStyleClass().remove("nav-btn-active");
            // Đảm bảo vẫn giữ class gốc nav-btn
            if (!btn.getStyleClass().contains("nav-btn")) {
                btn.getStyleClass().add("nav-btn");
            }
        }
        // Thêm class active cho nút vừa được chọn
        if (activeBtn != null && !activeBtn.getStyleClass().contains("nav-btn-active")) {
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }

    // ===== ĐIỀU HƯỚNG TỪ SIDEBAR =====
    @FXML
    protected void handleDashboard(ActionEvent event) {
        loadView("/com/nhomX/example/fxml/client/DashboardContent.fxml");
        setActiveButton(btnDashboard);
    }

    @FXML
    protected void handleLiveAuction(ActionEvent event) {
        loadView("/com/nhomX/example/fxml/client/LiveAuctionContent.fxml");
        setActiveButton(btnLiveAuction);
    }

    @FXML
    protected void handleMyAuctions(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            clearServerListener();
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/client/login.fxml");
            return;
        }
        loadView("/com/nhomX/example/fxml/client/MyAuctionsContent.fxml");
        setActiveButton(btnMyAuction);
    }

    @FXML
    protected void handleSeller(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            clearServerListener();
            SceneSwitcher.switchScene("/com/nhomX/example/fxml/client/login.fxml");
            return;
        }
        loadView("/com/nhomX/example/fxml/client/SellerContent.fxml");
        setActiveButton(btnSeller);
    }

    public void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node newNode = loader.load();
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(newNode);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi không thể tải trang con: " + fxmlPath);
        }
    }
    @FXML
    public void toggleWalletPanel(MouseEvent mouseEvent) {
        isWalletOpen = !isWalletOpen;

        // Ẩn/hiện vùng thông tin số dư
        walletPanel.setVisible(isWalletOpen);
        walletPanel.setManaged(isWalletOpen);

        // UX Animation: Xoay mũi tên 180 độ
        if (isWalletOpen) {
            walletArrowIcon.setRotate(180);
        } else {
            walletArrowIcon.setRotate(0);
        }
    }
    // ===== CƠ CHẾ REAL-TIME CẬP NHẬT SỐ DƯ =====
    // Hàm này có thể gọi từ BẤT KỲ ĐÂU (VD: AuctionClient, DepositController)
    public void updateBalanceGlobally() {
        if (SessionManager.getInstance().isLoggedIn()) {
            long currentBalance = SessionManager.getInstance().getCurrentUser().getBalance();

            // BẮT BUỘC dùng Platform.runLater vì hàm này có thể bị gọi từ luồng Socket (Thread con)
            Platform.runLater(() -> {
                // Giả định em đã có CurrencyFormatter. Nếu chưa, dùng String.format("%,d đ", currentBalance)
                lblCurrentBalance.setText(com.nhomX.example.utils.CurrencyFormatter.formatVND(currentBalance));
            });
        }
    }
    // ===== LOGIC HIỂN THỊ DỰA TRÊN TRẠNG THÁI LOGIN =====
    public void checkUserLoginStatus() {
        if (SessionManager.getInstance().isLoggedIn()) {
            // Đã đăng nhập: Hiện khu vực User Card
            sidebarUserArea.setVisible(true);
            sidebarUserArea.setManaged(true);

            // Nạp tên người dùng
            var currentUser = SessionManager.getInstance().getCurrentUser();
            String displayName = currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUserName();
            lblSidebarUserName.setText(displayName);

            // Cập nhật số dư lần đầu
            updateBalanceGlobally();
        } else {
            // Chưa đăng nhập: Giấu nhẹm toàn bộ khu vực này đi
            sidebarUserArea.setVisible(false);
            sidebarUserArea.setManaged(false);

            // Đảm bảo Wallet cũng đang đóng
            isWalletOpen = false;
            walletPanel.setVisible(false);
            walletPanel.setManaged(false);
        }
    }
    /**
     * Cất View hiện tại vào bộ đệm và chuyển sang màn hình Chi tiết mới.
     */
    public void saveCurrentViewAndNavigate(Node newDetailNode) {
        // Kiểm tra xem hiện tại đang có màn hình nào không
        if (!mainContentArea.getChildren().isEmpty()) {
            previousNode = mainContentArea.getChildren().get(0); // Lưu lại Node gốc (VD: Dashboard, LiveAuction)
        }

        // Gắn Node chi tiết mới vào
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(newDetailNode);
    }

    /**
     * Khôi phục lại View trước đó từ bộ đệm (Không tốn chi phí Load FXML lại).
     */
    public void restorePreviousView() {
        if (previousNode != null) {
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(previousNode);

            // Giải phóng tham chiếu để tránh Rò rỉ bộ nhớ (Memory Leak) nếu chuyển sang luồng khác
            previousNode = null;
        } else {
            // Fallback: Trở về an toàn nếu lịch sử bị rỗng
            loadView("/com/nhomX/example/fxml/client/DashboardContent.fxml");
        }
    }
}

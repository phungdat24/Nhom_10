package com.nhomX.example.controller.client;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.SceneSwitcher;
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

    // 5. Hàm nhận một Node đã có sẵn dữ liệu (Dùng cho ItemDetail)
    public void setCenterContent(Node node) {
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(node);
    }
}

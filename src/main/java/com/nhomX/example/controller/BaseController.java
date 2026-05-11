
package com.nhomX.example.controller;

import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.SceneSwitcher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;

public abstract class BaseController {

    // Các field này phải đặt tên GIỐNG HỆT trong tất cả file .fxml
    @FXML
    protected Button btnLogin;
    @FXML
    protected HBox userInfoBox;
    @FXML
    protected MenuButton menuUser;
    @FXML
    protected Label lblBalance;

    // ── Cập nhật header sau khi login/logout ──────────────────────────
    protected void updateHeaderUI() {
        if (btnLogin == null) return; // Màn hình không có header thì bỏ qua

        if (SessionManager.getInstance().isLoggedIn()) {
            User user = SessionManager.getInstance().getCurrentUser();

            menuUser.setText("👤  " + user.getFullName());
            if (lblBalance != null)
                lblBalance.setText(CurrencyFormatter.formatVND(user.getBalance()));

            btnLogin.setVisible(false);
            btnLogin.setManaged(false);
            userInfoBox.setVisible(true);
            userInfoBox.setManaged(true);
        } else {
            btnLogin.setVisible(true);
            btnLogin.setManaged(true);
            userInfoBox.setVisible(false);
            userInfoBox.setManaged(false);
        }
    }

    // ── Nav handlers dùng chung — viết 1 lần ─────────────────────────
    @FXML
    protected void handleDashboard(ActionEvent event) {
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/dashboard.fxml");
    }

    @FXML
    protected void handleLiveAuction(ActionEvent event) {
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/LiveAuction.fxml");
    }

    @FXML
    protected void handleMyAuctions(ActionEvent event) {
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/MyAuctions.fxml");
    }

    @FXML
    protected void handleSeller(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            SceneSwitcher.switchScene(event,
                    "/com/nhomX/example/fxml/login.fxml");
            return;
        }
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/Seller.fxml");
    }

    @FXML
    protected void handleProfile(ActionEvent event) {
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/Profile.fxml");
    }

    @FXML
    protected void handleLogin(ActionEvent event) {
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/login.fxml");
    }

    @FXML
    protected void handleLogout(ActionEvent event) {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) client.setServerEventListener(null);
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/login.fxml");
    }
}


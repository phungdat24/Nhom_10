
package com.nhomX.example.controller.client;

import com.nhomX.example.manager.SessionManager;
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

            menuUser.setText( user.getFullName());
            menuUser.getStyleClass().add("menu-button");
            if (lblBalance != null) {
                lblBalance.setText(CurrencyFormatter.formatVND(user.getBalance()));
            }
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
    // Hàm mới: Hỗ trợ dọn dẹp chống Memory Leak
    protected void clearServerListener() {
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(null);
        }
    }

    // ── Nav handlers dùng chung — viết 1 lần ─────────────────────────

    @FXML
    protected void handleProfile(ActionEvent event) {
        if (!SessionManager.getInstance().isLoggedIn()) {
            clearServerListener();
            SceneSwitcher.switchScene(event,
                    "/com/nhomX/example/fxml/login.fxml");
            return;
        }
        if (MainDashBoardController.instance != null) {
            MainDashBoardController.instance.loadView("/com/nhomX/example/fxml/ProfileContent.fxml");
        }
    }

    @FXML
    protected void handleLogin(ActionEvent event) {
        clearServerListener();
        SceneSwitcher.switchScene(event,
                "/com/nhomX/example/fxml/login.fxml");
    }

    @FXML
    protected void handleLogout(ActionEvent event) {
        clearServerListener();
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene("/com/nhomX/example/fxml/login.fxml");
    }
}



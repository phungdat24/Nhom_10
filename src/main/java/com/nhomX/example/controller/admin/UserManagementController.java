package com.nhomX.example.controller.admin;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class UserManagementController implements Initializable, ServerEventListener {

    private static final int PAGE_SIZE = 15;

    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private TextField searchField;
    @FXML private VBox tableBody;
    @FXML private HBox pageNumbersBox;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private Label pageInfoLabel;

    private final List<User> allUsers = new ArrayList<>();
    private final Map<String, HBox> rowCache = new HashMap<>();
    private final Map<String, Boolean> activeByUserId = new HashMap<>();
    private final NumberFormat vndFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    private List<User> filteredUsers = new ArrayList<>();
    private AuctionClient auctionClient;
    private int currentPage = 1;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupComboBoxes();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilterAndRender());

        auctionClient = SessionManager.getInstance().getAuctionClient();
        if (auctionClient == null) {
            System.err.println("CLIENT: Chưa khởi tạo AuctionClient cho màn hình quản lý người dùng.");
            applyFilterAndRender();
            return;
        }

        auctionClient.addListener(this);
        auctionClient.sendToServer(new Message("GET_ALL_USERS"));
    }

    private void setupComboBoxes() {
        statusComboBox.getItems().addAll("Tất cả", "Đang hoạt động", "Bị khóa");
        statusComboBox.getSelectionModel().selectFirst();
        statusComboBox.setOnAction(event -> applyFilterAndRender());

        roleComboBox.getItems().addAll("Tất cả vai trò", "ADMIN", "BIDDER", "SELLER");
        roleComboBox.getSelectionModel().selectFirst();
        roleComboBox.setOnAction(event -> applyFilterAndRender());
    }

    @Override
    public void onAllUsersReceived(List<User> users) {
        Platform.runLater(() -> {
            allUsers.clear();
            if (users != null) {
                allUsers.addAll(users);
                users.forEach(user -> activeByUserId.putIfAbsent(user.getId(), true));
            }
            activeByUserId.keySet().removeIf(userId ->
                    allUsers.stream().noneMatch(user -> Objects.equals(user.getId(), userId)));
            currentPage = 1;
            applyFilterAndRender();
        });
    }

    @Override
    public void onUserBalanceUpdated(String userId, long newBalance) {
        Platform.runLater(() -> {
            allUsers.stream()
                    .filter(user -> Objects.equals(user.getId(), userId))
                    .findFirst()
                    .ifPresent(user -> user.setBalance(newBalance));

            HBox row = rowCache.get(userId);
            if (row != null) {
                Label balanceLabel = (Label) row.lookup(".user-management-money-cell");
                if (balanceLabel != null) {
                    balanceLabel.setText(formatVnd(newBalance));
                }
            }
        });
    }

    @Override
    public void onUserStatusChanged(String userId, boolean isActive) {
        Platform.runLater(() -> {
            activeByUserId.put(userId, isActive);
            applyFilterAndRender();
        });
    }

    private void applyFilterAndRender() {
        String keyword = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusComboBox.getValue();
        String selectedRole = roleComboBox.getValue();

        filteredUsers = allUsers.stream()
                .filter(user -> matchesStatus(user, selectedStatus))
                .filter(user -> matchesRole(user, selectedRole))
                .filter(user -> matchesKeyword(user, keyword))
                .collect(Collectors.toList());

        currentPage = 1;
        renderCurrentPage();
    }

    private boolean matchesStatus(User user, String status) {
        if (status == null || status.equals("Tất cả")) {
            return true;
        }
        boolean active = isUserActive(user);
        return status.equals("Đang hoạt động") ? active : !active;
    }

    private boolean matchesRole(User user, String role) {
        if (role == null || role.equals("Tất cả vai trò")) {
            return true;
        }
        return user.getRoleName() != null && user.getRoleName().contains(role);
    }

    private boolean matchesKeyword(User user, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(user.getFullName(), keyword)
                || containsIgnoreCase(user.getUserName(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void renderCurrentPage() {
        tableBody.getChildren().clear();
        rowCache.clear();

        int total = filteredUsers.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        currentPage = Math.min(currentPage, totalPages);

        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        for (int index = fromIndex; index < toIndex; index++) {
            User user = filteredUsers.get(index);
            HBox row = renderUserRow(user);
            rowCache.put(user.getId(), row);
            tableBody.getChildren().add(row);
        }

        int displayFrom = total == 0 ? 0 : fromIndex + 1;
        updatePaginationBar(total, displayFrom, toIndex, totalPages);
    }

    private HBox renderUserRow(User user) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("user-management-table-row");

        Label idLabel = new Label("#" + user.getId());
        idLabel.setPrefWidth(130);
        idLabel.getStyleClass().add("user-management-body-cell");

        String initials = buildInitials(user.getFullName());
        Label avatar = new Label(initials);
        avatar.getStyleClass().add(pickAvatarStyle(initials));

        Label nameLabel = new Label(valueOrDash(user.getFullName()));
        nameLabel.getStyleClass().add("user-management-name-cell");

        HBox nameCell = new HBox(12, avatar, nameLabel);
        nameCell.setPrefWidth(300);
        nameCell.setAlignment(Pos.CENTER_LEFT);

        Label emailLabel = new Label(valueOrDash(user.getUserName()));
        emailLabel.setPrefWidth(320);
        emailLabel.getStyleClass().add("user-management-body-cell");

        Label balanceLabel = new Label(formatVnd(user.getBalance()));
        balanceLabel.setPrefWidth(220);
        balanceLabel.getStyleClass().add("user-management-money-cell");

        boolean active = isUserActive(user);
        Label statusBadge = new Label(active ? "• ĐANG HOẠT ĐỘNG" : "• BỊ KHÓA");
        statusBadge.getStyleClass().add(
                active ? "user-management-status-active" : "user-management-status-locked");

        HBox statusCell = new HBox(statusBadge);
        statusCell.setPrefWidth(230);
        statusCell.setAlignment(Pos.CENTER_LEFT);

        Label toggleStatusAction = new Label(active ? "KHÓA" : "MỞ");
        toggleStatusAction.getStyleClass().add("user-management-action-view");
        toggleStatusAction.setOnMouseClicked(event -> handleToggleStatus(user));
        toggleStatusAction.setStyle("-fx-cursor: hand;");

        Label deleteAction = new Label("XÓA");
        deleteAction.getStyleClass().add("user-management-action-lock");
        deleteAction.setOnMouseClicked(event -> handleDeleteUser(user));
        deleteAction.setStyle("-fx-cursor: hand;");

        HBox actionCell = new HBox(16, toggleStatusAction, deleteAction);
        actionCell.setPrefWidth(140);
        actionCell.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(idLabel, nameCell, emailLabel, balanceLabel, statusCell, actionCell);
        return row;
    }

    private void updatePaginationBar(int total, int from, int to, int totalPages) {
        pageInfoLabel.setText(String.format(
                "Đang xem %d - %d của %s người dùng", from, to, vndFormat.format(total)));

        prevButton.setDisable(currentPage <= 1);
        nextButton.setDisable(currentPage >= totalPages);
        pageNumbersBox.getChildren().clear();

        int start = Math.max(1, currentPage - 2);
        int end = Math.min(totalPages, start + 4);
        start = Math.max(1, end - 4);

        if (start > 1) {
            addPageButton(1, "user-management-page-ghost");
        }
        for (int page = start; page <= end; page++) {
            addPageButton(page, page == currentPage
                    ? "user-management-page-active"
                    : "user-management-page-button");
        }
        if (end < totalPages) {
            addPageButton(totalPages, "user-management-page-ghost");
        }
    }

    private void addPageButton(int page, String styleClass) {
        Button button = new Button(String.valueOf(page));
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> {
            currentPage = page;
            renderCurrentPage();
        });
        pageNumbersBox.getChildren().add(button);
    }

    @FXML
    private void handlePrev() {
        if (currentPage > 1) {
            currentPage--;
            renderCurrentPage();
        }
    }

    @FXML
    private void handleNext() {
        int totalPages = (int) Math.ceil((double) filteredUsers.size() / PAGE_SIZE);
        if (currentPage < totalPages) {
            currentPage++;
            renderCurrentPage();
        }
    }

    @FXML
    private void handleAddUser() {
        // TODO: Open the create-user dialog.
    }

    @FXML
    private void handleExport() {
        // TODO: Export the filtered users.
    }

    private void handleToggleStatus(User user) {
        if (auctionClient != null) {
            auctionClient.sendToServer(new Message("TOGGLE_USER_STATUS", user.getId()));
        }
    }

    private void handleDeleteUser(User user) {
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Xác nhận xóa người dùng: " + valueOrDash(user.getFullName()) + "?",
                ButtonType.YES,
                ButtonType.NO);
        confirmation.showAndWait().ifPresent(button -> {
            if (button == ButtonType.YES && auctionClient != null) {
                auctionClient.sendToServer(new Message("DELETE_USER", user.getId()));
            }
        });
    }

    private boolean isUserActive(User user) {
        return activeByUserId.getOrDefault(user.getId(), true);
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "??";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private String pickAvatarStyle(String initials) {
        if (initials.isEmpty()) {
            return "user-management-avatar-gray";
        }
        return switch (initials.charAt(0)) {
            case 'A', 'B', 'C', 'D' -> "user-management-avatar-blue";
            case 'E', 'F', 'G', 'H' -> "user-management-avatar-orange";
            default -> "user-management-avatar-gray";
        };
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String formatVnd(long amount) {
        return vndFormat.format(amount) + " đ";
    }

    public void onClose() {
        if (auctionClient != null) {
            auctionClient.removeListener(this);
        }
    }
}

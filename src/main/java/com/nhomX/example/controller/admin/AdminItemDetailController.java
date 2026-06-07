package com.nhomX.example.controller.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.controller.client.BaseController;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public class AdminItemDetailController extends BaseController implements ServerEventListener {
  private static final Logger logger = LoggerFactory.getLogger(AdminItemDetailController.class);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm - dd/MM");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  @FXML
  private Button btnBack;
  @FXML
  private Button btnClose;
  @FXML
  private ImageView imgMain;
  @FXML
  private HBox hboxThumbnails;
  @FXML
  private Label lblTitle;
  @FXML
  private Label lblCode;
  @FXML
  private Label lblAvatarText;
  @FXML
  private Label lblSellerName;
  @FXML
  private Label lblSubmitDate;
  @FXML
  private Label lblDescription;
  @FXML
  private Label lblStartingPrice;
  @FXML
  private Label lblDesiredTime;
  @FXML
  private TextArea txtRejectReason;
  @FXML
  private Button btnReject;
  @FXML
  private Button btnApprove;

  private Auction currentAuction;
  private AuctionClient auctionClient;

  @FXML
  public void initialize() {
    auctionClient = SessionManager.getInstance().getAuctionClient();
    if (auctionClient != null) {
      auctionClient.setServerEventListener(this);
    }
  }

  /**
   * Nạp dữ liệu sản phẩm từ danh sách truyền sang.
   */
  public void setAuctionData(Auction pendingAuction) {
    this.currentAuction = pendingAuction;
    if (pendingAuction == null || pendingAuction.getItem() == null) {
      return;
    }

    // Đổ dữ liệu cơ bản
    Items item = pendingAuction.getItem();
    setLabelText(lblTitle, item.getTitle());
    setLabelText(lblCode, "MÃ SP: #" + shortAuctionCode(pendingAuction.getId()));
    setLabelText(lblDescription, item.getDescription());
    setLabelText(lblStartingPrice, CurrencyFormatter.formatVND(pendingAuction.getStartingPrice()));

    // Thông tin người bán
    // Seller nằm trong Item của phiên, không nằm trực tiếp trên Auction.
    User seller = item.getSeller();
    String sellerName = getSellerDisplayName(seller);
    setLabelText(lblSellerName, sellerName);
    setLabelText(lblAvatarText,
        sellerName.isBlank() ? "?" : sellerName.substring(0, 1).toUpperCase());

    // Thời gian (Giả định ngày gửi là ngày tạo phiên - nếu có trong model,
    // ở đây dùng tạm logic hiện tại)
    if (pendingAuction.getStartTime() != null) {
      setLabelText(lblDesiredTime, pendingAuction.getStartTime().format(TIME_FORMATTER));
      // Tạm thời lấy startTime làm mốc ngày gửi hiển thị
      setLabelText(lblSubmitDate, pendingAuction.getStartTime().format(DATE_FORMATTER));
    } else {
      setLabelText(lblDesiredTime, "Chưa xác định");
      setLabelText(lblSubmitDate, LocalDateTime.now().format(DATE_FORMATTER));
    }

    if (hboxThumbnails != null) {
      hboxThumbnails.getChildren().clear(); // Dọn dẹp ảnh của phiên trước đó
    }

    // Tải ảnh bất đồng bộ
    List<ItemImage> images = item.getImages();
    if (images != null && !images.isEmpty()) {
      // 1. Tải ảnh đầu tiên làm ảnh chính
      if (imgMain != null) ImageLoader.loadAsync(images.get(0).getImagePath(), imgMain);

      // 2. Tạo dải ảnh thu nhỏ (Thumbnails)
      if (hboxThumbnails != null) {
        for (int i = 0; i < images.size(); i++) {
          String imgPath = images.get(i).getImagePath();

          // Tạo khung chứa (StackPane) để dùng CSS bo góc và viền
          StackPane thumbWrapper = new StackPane();
          thumbWrapper.setPrefSize(88, 88);
          thumbWrapper.setMinSize(88, 88);

          // Mặc định ảnh đầu tiên được viền sáng (active)
          if (i == 0) {
            thumbWrapper.getStyleClass().add("admin-item-detail-thumb-frame-active");
          } else {
            thumbWrapper.getStyleClass().add("admin-item-detail-thumb-frame");
          }
          thumbWrapper.setStyle("-fx-cursor: hand;"); // Hiện bàn tay khi rê chuột

          // Tạo ImageView cho ảnh nhỏ
          ImageView thumbView = new ImageView();
          thumbView.setFitHeight(88);
          thumbView.setFitWidth(88);
          thumbView.setPreserveRatio(true);
          thumbView.setSmooth(true);

          // Nhờ ImageLoader lấy ảnh về
          ImageLoader.loadAsync(imgPath, thumbView);
          thumbWrapper.getChildren().add(thumbView);

          // 3. Sự kiện bấm vào ảnh nhỏ thì load lên ảnh chính
          final int clickedIndex = i;
          thumbWrapper.setOnMouseClicked(e -> {
            if (imgMain != null) ImageLoader.loadAsync(imgPath, imgMain);
            updateThumbnailSelection(clickedIndex); // Đổi màu viền
          });

          hboxThumbnails.getChildren().add(thumbWrapper);
        }
      }
    } else {
      // Nếu Seller quên up ảnh, ép hiển thị Placeholder
      if (imgMain != null) ImageLoader.loadAsync(null, imgMain);
    }
  }
  // Hàm phụ trợ: Xóa viền cũ và Nhuộm viền sáng cho ảnh nhỏ đang được chọn
  private void updateThumbnailSelection(int selectedIndex) {
    if (hboxThumbnails == null || hboxThumbnails.getChildren().isEmpty()) return;

    for (int i = 0; i < hboxThumbnails.getChildren().size(); i++) {
      Node node = hboxThumbnails.getChildren().get(i);
      node.getStyleClass().removeAll("admin-item-detail-thumb-frame-active", "admin-item-detail-thumb-frame");

      if (i == selectedIndex) {
        node.getStyleClass().add("admin-item-detail-thumb-frame-active");
      } else {
        node.getStyleClass().add("admin-item-detail-thumb-frame");
      }
    }
  }

  @FXML
  private void handleApproveAction(ActionEvent event) {
    if (currentAuction == null || auctionClient == null) {
      return;
    }

    // Khóa các nút để tránh click nhiều lần
    setButtonsDisable(true);
    // Gửi lệnh duyệt lên sàn
    auctionClient.approveAuction(currentAuction.getId());
    logger.info("ADMIN: Đang gửi lệnh DUYỆT cho phiên {}", currentAuction.getId());
  }

  @FXML
  private void handleRejectAction(ActionEvent event) {
    if (currentAuction == null || auctionClient == null) {
      return;
    }

    String rejectReason = txtRejectReason != null ? txtRejectReason.getText().trim() : "";
    if (rejectReason.isEmpty()) {
      AlertUtils.showWarning("Yêu cầu bắt buộc",
          "Vui lòng nhập lý do từ chối để người bán có thể chỉnh sửa lại sản phẩm.");
      if (txtRejectReason != null) {
        txtRejectReason.requestFocus();
      }
      return;
    }

    // Khóa các nút để tránh click nhiều lần
    setButtonsDisable(true);
    // Gửi lệnh từ chối. Server hiện nhận auctionId, phần lý do chỉ dùng để bắt buộc Admin nhập.
    auctionClient.rejectAuction(currentAuction.getId());
    logger.info("ADMIN: Đang gửi lệnh TỪ CHỐI cho phiên {}", currentAuction.getId());
  }

  @FXML
  private void handleBackAction(ActionEvent event) {
    clearServerListener();
    // Quay lại trang quản lý sản phẩm trong AdminLayoutController.
    if (AdminLayoutController.instance != null) {
      AdminLayoutController.instance.handleNavProducts(null);
    } else {
      logger.error("AdminLayoutController instance bị null");
    }
  }

  @FXML
  private void handleCloseAction(ActionEvent event) {
    handleBackAction(event);
  }

  @Override
  public void onAdminActionCompleted(boolean isSuccess, String message) {
    Platform.runLater(() -> {
      setButtonsDisable(false); // Mở khóa nút bấm nếu cần
      if (isSuccess) {
        AlertUtils.showSuccess("Hoàn tất", message);
        // Hành động thành công -> Tự động quay lại danh sách
        handleBackAction(null);
      } else {
        AlertUtils.showError("Thất bại", message);
      }
    });
  }

  /**
   * Hàm hỗ trợ khóa/mở khóa nút bấm trong lúc chờ mạng.
   */
  private void setButtonsDisable(boolean disable) {
    if (btnApprove != null) {
      btnApprove.setDisable(disable);
    }
    if (btnReject != null) {
      btnReject.setDisable(disable);
    }
    if (txtRejectReason != null) {
      txtRejectReason.setDisable(disable);
    }
  }

  private String shortAuctionCode(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return "UNKNOWN";
    }
    return auctionId.substring(0, Math.min(8, auctionId.length())).toUpperCase();
  }

  private String getSellerDisplayName(User seller) {
    if (seller == null) {
      return "Ẩn danh";
    }
    if (seller.getFullName() != null && !seller.getFullName().isBlank()) {
      return seller.getFullName();
    }
    if (seller.getUserName() != null && !seller.getUserName().isBlank()) {
      return seller.getUserName();
    }
    return "Ẩn danh";
  }

  private void setLabelText(Label label, String text) {
    if (label != null) {
      label.setText(text != null ? text : "");
    }
  }
}

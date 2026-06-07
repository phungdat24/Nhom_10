package com.nhomX.example.controller.client;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.factory.ItemFactory;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.Message;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AddItemcardController implements Initializable, ServerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(AddItemcardController.class);
    // =========================================================================
    // 1. KHAI BÁO CÁC COMPONENT TỪ FXML (@FXML)
    // =========================================================================

    @FXML
    private FlowPane imageFlowPane;
    @FXML
    private Button btnUploadImg;

    @FXML
    private TextField txtProductName;
    @FXML
    private ComboBox<String> cbCategory;
    @FXML
    private TextArea txtProductDescription;
    @FXML
    private TextField txtStartPrice;

    @FXML
    private DatePicker dpStartDate;
    @FXML
    private Spinner<Integer> spStartHour;
    @FXML
    private Spinner<Integer> spStartMin;

    @FXML
    private DatePicker dpEndDate;
    @FXML
    private Spinner<Integer> spEndHour;
    @FXML
    private Spinner<Integer> spEndMin;

    @FXML
    private Button btnCancel;
    @FXML
    private Button btnSubmit;
    @FXML
    private Label lblPriceInWords; // <-- THÊM DÒNG NÀY

    private static final int MAX_IMAGES = 5;

    // Thêm một biến cờ (flag) để kiểm soát sự kiện gõ phím
    private boolean isFormattingPrice = false;
    private boolean isEditMode = false;
    private String editingItemId = null;
    private String editingAuctionId = null;
    private Auction editingAuction = null;
    private Items pendingUpdatedItem = null;
    private Auction pendingUpdatedAuction = null;

    // Danh sách lưu trữ đường dẫn các ảnh đã tải lên
    private final List<String> uploadedImagePaths = new ArrayList<>();
    private final List<String> existingImagePaths = new ArrayList<>();

    // =========================================================================
    // 2. PHƯƠNG THỨC KHỞI TẠO (Chạy tự động khi load FXML)
    // =========================================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupCategoryComboBox();
        setupTimeSpinners();
        setupPriceFormatter();
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            client.setServerEventListener(this);
            logger.info("ADD ITEM POP-UP: Đã giành quyền lắng nghe sự kiện từ Server!");
        }
    }

    /**
     * Nạp dữ liệu phiên hiện tại vào form sửa sản phẩm.
     */
    public void initForEdit(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            return;
        }

        this.isEditMode = true;
        this.editingAuction = auction;
        this.editingAuctionId = auction.getId();
        this.editingItemId = auction.getItem().getId();

        txtProductName.setText(auction.getItem().getTitle());
        txtProductDescription.setText(auction.getItem().getDescription());
        cbCategory.setValue(auction.getItem().getCategory());
        txtStartPrice.setText(CurrencyFormatter.formatNumber(auction.getStartingPrice()));

        if (auction.getStartTime() != null) {
            dpStartDate.setValue(auction.getStartTime().toLocalDate());
            spStartHour.getValueFactory().setValue(auction.getStartTime().getHour());
            spStartMin.getValueFactory().setValue(auction.getStartTime().getMinute());
        }
        if (auction.getEndTime() != null) {
            dpEndDate.setValue(auction.getEndTime().toLocalDate());
            spEndHour.getValueFactory().setValue(auction.getEndTime().getHour());
            spEndMin.getValueFactory().setValue(auction.getEndTime().getMinute());
        }

        imageFlowPane.getChildren().clear();
        existingImagePaths.clear();
        if (auction.getItem().getImages() != null) {
            for (ItemImage image : auction.getItem().getImages()) {
                if (image.getImagePath() != null && !image.getImagePath().trim().isEmpty()) {
                    addExistingImageThumbnail(image.getImagePath().trim());
                }
            }
        }

        btnSubmit.setText("Cập nhật sản phẩm");
    }

    private void setupCategoryComboBox() {
        ObservableList<String> categories =
                FXCollections.observableArrayList("ELECTRONICS", "JEWELRY", "ART", "GENERALITEM");
        cbCategory.setItems(categories);
    }

    /**
     * Cấu hình giới hạn cho các Spinner chọn Giờ (0-23) và Phút (0-59). Lý do: Đảm bảo người dùng
     * không thể nhập thời gian vô lý như "25 giờ 61 phút".
     */
    private void setupTimeSpinners() {
        // Cấu hình SpinnerFactory cho Giờ (min=0, max=23, default=8)
        spStartHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 8));
        spEndHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 23));

        // Cấu hình SpinnerFactory cho Phút (min=0, max=59, default=0)
        spStartMin.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        spEndMin.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 59));
    }

    /**
     * Ràng buộc TextField Giá khởi điểm chỉ cho phép nhập số. Lý do: Ngăn chặn lỗi
     * NumberFormatException khi chuyển đổi String sang Double/Integer lúc lưu DB.
     */
    private void setupPriceFormatter() {
        txtStartPrice.textProperty().addListener((observable, oldValue, newValue) -> {
            // Nếu code đang tự động format thì bỏ qua
            if (isFormattingPrice)
                return;

            // 1. Lọc bỏ tất cả ký tự không phải là số
            String rawNumber = newValue.replaceAll("[^\\d]", "");

            if (rawNumber.isEmpty()) {
                isFormattingPrice = true;
                txtStartPrice.setText("");
                lblPriceInWords.setText("");
                isFormattingPrice = false;
                return;
            }

            // 2. CHỐT CHẶN BẢO MẬT: Giới hạn độ dài tối đa là 18 chữ số (Dưới 1 tỷ tỷ)
            // Ngăn chặn triệt để lỗi NumberFormatException do vượt quá giới hạn của kiểu Long
            if (rawNumber.length() > 18) {
                rawNumber = rawNumber.substring(0, 18);
            }

            try {
                // BẬT CỜ NGAY ĐẦU KHỐI TRY
                isFormattingPrice = true;

                long amount = Long.parseLong(rawNumber);
                String formattedWithDots = CurrencyFormatter.formatNumber(amount);

                txtStartPrice.setText(formattedWithDots);
                lblPriceInWords.setText(CurrencyFormatter.numberToWords(amount) + " đồng");

                // 3. XỬ LÝ XUNG ĐỘT LUỒNG JAVAFX
                // Dùng Platform.runLater để yêu cầu JavaFX: "Đợi thao tác xóa/gõ hiện tại hoàn tất
                // rồi mới dời con trỏ nhé"
                Platform.runLater(() -> {
                    txtStartPrice.positionCaret(txtStartPrice.getText().length());
                });

            } catch (Exception e) {
                // Lỡ có lỗi không xác định, khôi phục lại giá trị cũ an toàn
                txtStartPrice.setText(oldValue);
                logger.error("Lỗi format tiền", e);
            } finally {
                // 4. CHÌA KHÓA VÀNG: Khối finally LUÔN LUÔN CHẠY dù có lỗi hay không.
                // Đảm bảo cờ trạng thái không bao giờ bị kẹt lại!
                isFormattingPrice = false;
            }
        });
    }

    // =========================================================================
    // 3. XỬ LÝ SỰ KIỆN NÚT BẤM (Actions)
    // =========================================================================

    /**
     * Xử lý khi nhấn nút "+ Thêm ảnh".
     */
    @FXML
    private void handleAddImage(ActionEvent event) {
        int remaining = MAX_IMAGES - currentImageCount();
        if (remaining <= 0) {
            AlertUtils.showWarning("Đã đủ ảnh",
                    "Bạn chỉ được tải lên tối đa " + MAX_IMAGES + " ảnh.");
            return;
        }
        // Sử dụng FileChooser mặc định của hệ điều hành để chọn file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm (còn " + remaining + " ảnh)");
        // Lọc chỉ cho phép chọn file ảnh
        fileChooser.getExtensionFilters()
                .addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));

        // Lấy Stage (cửa sổ hiện tại) để hiển thị hộp thoại chọn file
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null || selectedFiles.isEmpty())
            return;
        // Chỉ lấy đủ số ảnh còn được phép
        int canAdd = Math.min(selectedFiles.size(), remaining);
        for (int i = 0; i < canAdd; i++) {
            addImageThumbnail(selectedFiles.get(i).toURI().toString());
        }

        if (selectedFiles.size() > canAdd) {
            AlertUtils.showWarning("Giới hạn ảnh", "Chỉ " + canAdd
                    + " ảnh đầu được thêm. Đã đạt giới hạn " + MAX_IMAGES + " ảnh.");
        }
    }

    /**
     * Tạo thumbnail có nút "✕" để xóa, rồi gắn vào imageFlowPane.
     */
    private void addImageThumbnail(String imageUri) {
        uploadedImagePaths.add(imageUri);

        ImageView imageView = buildThumbnailImageView(imageUri);
        Button btnRemove = buildRemoveButton();
        StackPane wrapper = new StackPane(imageView, btnRemove);

        StackPane.setAlignment(btnRemove, javafx.geometry.Pos.TOP_RIGHT);

        // Khi bấm "✕", tìm đúng vị trí của wrapper trong FlowPane để xóa đồng bộ
        btnRemove.setOnAction(e -> removeUploadedImage(wrapper, imageUri));

        imageFlowPane.getChildren().add(wrapper);
    }

    private void addExistingImageThumbnail(String fileName) {
        existingImagePaths.add(fileName);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(80);
        imageView.setFitHeight(80);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 4, 0, 0, 0);");
        ImageLoader.loadAsync(fileName, imageView);

        imageFlowPane.getChildren().add(new StackPane(imageView));
    }

    private int currentImageCount() {
        return uploadedImagePaths.size() + existingImagePaths.size();
    }

    private Button buildRemoveButton() {
        Button btn = new Button("✕");
        btn.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; "
                + "-fx-font-size: 9px; -fx-padding: 1 4; "
                + "-fx-background-radius: 10; -fx-cursor: hand;");
        return btn;
    }

    private ImageView buildThumbnailImageView(String imageUri) {
        ImageView iv = new ImageView(new Image(imageUri, true));
        iv.setFitWidth(80);
        iv.setFitHeight(80);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 4, 0, 0, 0);");
        return iv;
    }

    /**
     * Xóa thumbnail và URI tương ứng ra khỏi danh sách.
     */
    private void removeUploadedImage(StackPane wrapper, String imageUri) {
        uploadedImagePaths.remove(imageUri);
        imageFlowPane.getChildren().remove(wrapper);
    }

    /**
     * Xử lý khi nhấn nút "Thêm sản phẩm".
     */
    @FXML
    private void handleSubmit(ActionEvent event) {
        if (!validateInputs()) {
            return; // Nếu dữ liệu không hợp lệ, dừng ngay việc submit
        }
        setSubmitLoading(true);
        try {
            Items newItem = buildItem();
            Auction newAuction = isEditMode ? buildAuctionForEdit(newItem) : buildAuction(newItem);
            Map<String, byte[]> imageDataMap = buildImageDataMap(newItem.getId());
            pendingUpdatedItem = newItem;
            pendingUpdatedAuction = newAuction;

            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client == null) {
                AlertUtils.showError("Lỗi kết nối", "Không tìm thấy kết nối tới Server.");
                setSubmitLoading(false);
                return;
            }

            if (isEditMode) {
                Object[] payload = {newItem, newAuction, imageDataMap};
                client.sendToServer(new Message("UPDATE_PRODUCT", client.getUsername(),
                        newAuction.getId(), 0, payload));
            } else {
                client.requestCreateAuction(newItem, newAuction, imageDataMap);
            }

        } catch (Exception e) {
            logger.error("ADD ITEM: Lỗi đóng gói dữ liệu", e);
            AlertUtils.showError("Lỗi", "Không thể đọc hoặc nén dữ liệu ảnh. Vui lòng thử lại.");
            setSubmitLoading(false);
        }
    }

    private Items buildItem() {
        String itemId = isEditMode ? editingItemId : UUID.randomUUID().toString();
        String name = txtProductName.getText().trim();
        String category = cbCategory.getValue();
        String description = txtProductDescription.getText().trim();
        RegularUser seller = (RegularUser) SessionManager.getInstance().getCurrentUser();

        Items item = ItemFactory.createItem(category, itemId, name, description, seller);
        if (isEditMode) {
            for (String imagePath : existingImagePaths) {
                item.addImage(new ItemImage(UUID.randomUUID().toString(), imagePath, itemId));
            }
        }
        return item;
    }

    private Auction buildAuction(Items item) {
        String auctionId = UUID.randomUUID().toString();
        long startPrice = parsedPrice();
        LocalDateTime start = buildLocalDateTime(dpStartDate, spStartHour, spStartMin);
        LocalDateTime end = buildLocalDateTime(dpEndDate, spEndHour, spEndMin);

        Auction auction = new Auction(auctionId, item, start, end, startPrice);
        auction.setStatus(AuctionStatus.PENDING); // Chờ Admin duyệt
        return auction;
    }

    /**
     * Đọc từng file ảnh thành byte[], đặt tên file duy nhất có chứa UUID item.
     */
    private Auction buildAuctionForEdit(Items item) {
        long startPrice = parsedPrice();

        LocalDateTime start =
                buildLocalDateTime(dpStartDate, spStartHour, spStartMin);

        LocalDateTime end =
                buildLocalDateTime(dpEndDate, spEndHour, spEndMin);

        Auction auction =
                new Auction(editingAuctionId, item, start, end, startPrice);

        /*
         * Mọi nội dung được seller cập nhật đều phải được admin duyệt lại.
         *
         * Không giữ trạng thái UP_COMING cũ vì trạng thái đó chỉ có ý nghĩa
         * đối với phiên bản sản phẩm đã được admin duyệt trước khi chỉnh sửa.
         */
        auction.setStatus(AuctionStatus.PENDING);
        auction.setApprovedBy(null);

        return auction;
    }

    private Map<String, byte[]> buildImageDataMap(String itemId) throws Exception {
        Map<String, byte[]> imageDataMap = new LinkedHashMap<>();
        String shortId = itemId.substring(0, 8);
        int startIndex = isEditMode ? existingImagePaths.size() : 0;

        for (int i = 0; i < uploadedImagePaths.size(); i++) {
            String uriPath = uploadedImagePaths.get(i);
            byte[] bytes = Files.readAllBytes(Paths.get(URI.create(uriPath)));
            String fileName = "item_" + shortId + "_" + (startIndex + i) + ".jpg";
            imageDataMap.put(fileName, bytes);
        }
        return imageDataMap;
    }

    /**
     * Xử lý khi nhấn nút "Hủy bỏ".
     */
    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow(event);
    }

    // =========================================================================
    // 4. CÁC PHƯƠNG THỨC TIỆN ÍCH (Utility Methods)
    // =========================================================================

    /**
     * Kiểm tra tính hợp lệ của dữ liệu trước khi xử lý (Validation).
     * 
     * @return true nếu tất cả hợp lệ, false nếu có lỗi.
     */
    private boolean validateInputs() {
        // Kiểm tra bỏ trống
        if (txtProductName.getText().trim().isEmpty() || cbCategory.getValue() == null
                || txtStartPrice.getText().trim().isEmpty()) {
            AlertUtils.showError("Lỗi Nhập Liệu", "Vui lòng điền đầy đủ các trường bắt buộc (*).");
            return false;
        }

        // Kiểm tra thời gian
        if (dpStartDate.getValue() == null || dpEndDate.getValue() == null) {
            AlertUtils.showError("Lỗi Nhập Liệu", "Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc.");
            return false;
        }

        LocalDateTime start = LocalDateTime.of(dpStartDate.getValue(),
                LocalTime.of(spStartHour.getValue(), spStartMin.getValue()));
        LocalDateTime end = LocalDateTime.of(dpEndDate.getValue(),
                LocalTime.of(spEndHour.getValue(), spEndMin.getValue()));

        if (start.isBefore(LocalDateTime.now())) {
            AlertUtils.showError("Cảnh Báo Thời Gian",
                    "Thời gian bắt đầu không được nằm trong quá khứ.");
            return false;
        }

        if (end.isBefore(start) || end.isEqual(start)) {
            AlertUtils.showError("Cảnh Báo Thời Gian",
                    "Thời gian kết thúc phải diễn ra SAU thời gian bắt đầu.");
            return false;
        }

        // Kiểm tra có ít nhất 1 ảnh (Tùy chọn, tùy vào rule của em)
        if (currentImageCount() == 0) {
            AlertUtils.showError("Thiếu Ảnh", "Vui lòng tải lên ít nhất một ảnh sản phẩm.");
            return false;
        }

        return true;
    }

    private LocalDateTime buildLocalDateTime(DatePicker dp, Spinner<Integer> hourSpinner,
            Spinner<Integer> minSpinner) {
        return LocalDateTime.of(dp.getValue(),
                LocalTime.of(hourSpinner.getValue(), minSpinner.getValue()));
    }

    private long parsedPrice() {
        try {
            String raw = txtStartPrice.getText().replaceAll("[^\\d]", "");
            return raw.isEmpty() ? 0L : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void setSubmitLoading(boolean isLoading) {
        btnSubmit.setDisable(isLoading);
        if (isLoading) {
            btnSubmit.setText(isEditMode ? "Đang cập nhật..." : "Đang tải lên...");
        } else {
            btnSubmit.setText(isEditMode ? "Cập nhật sản phẩm" : "Thêm sản phẩm");
        }
    }

    /**
     * Đóng cửa sổ (Stage) hiện tại.
     */
    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void applyEditResultToCurrentAuction() {
        if (!isEditMode
                || editingAuction == null
                || pendingUpdatedAuction == null
                || pendingUpdatedItem == null) {
            return;
        }

        editingAuction.setItem(pendingUpdatedItem);

        editingAuction.setStartingPrice(
                pendingUpdatedAuction.getStartingPrice());

        editingAuction.setHighestBid(
                pendingUpdatedAuction.getStartingPrice());

        editingAuction.setStartTime(
                pendingUpdatedAuction.getStartTime());

        editingAuction.setEndTime(
                pendingUpdatedAuction.getEndTime());

        /*
         * Sau khi chỉnh sửa phải hiển thị "CHỜ DUYỆT",
         * không tiếp tục hiển thị "SẮP LÊN SÀN".
         */
        editingAuction.setStatus(AuctionStatus.PENDING);
        editingAuction.setApprovedBy(null);
    }

    @Override
    public void onCreateAuctionResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            setSubmitLoading(false);

            if (isSuccess) {
                applyEditResultToCurrentAuction();
                AlertUtils.showSuccess("Thành công", message);
                // Đóng cửa sổ (Tương đương gọi nút Cancel)
                btnCancel.fire();
            } else {
                AlertUtils.showError("Thất bại", message);
            }
        });
    }
}

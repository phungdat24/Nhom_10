package com.nhomX.example.controller.client;

import com.nhomX.example.factory.ItemFactory;
import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.networking.ServerEventListener;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class AddItemcardController implements Initializable, ServerEventListener {
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

    // Danh sách lưu trữ đường dẫn các ảnh đã tải lên
    private final List<String> uploadedImagePaths = new ArrayList<>();

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
            System.out.println("ADD ITEM POP-UP: Đã giành quyền lắng nghe sự kiện từ Server!");
        }
    }

    /**
     * Khởi tạo dữ liệu cho ComboBox Danh mục.
     * Lý do: Cố định các lựa chọn để dữ liệu lưu vào Database được đồng nhất (tránh người dùng gõ sai chính tả).
     */
    private void setupCategoryComboBox() {
        ObservableList<String> categories = FXCollections.observableArrayList(
                "ELECTRONICS", "JEWELRY", "ART", "GENERALITEM"
        );
        cbCategory.setItems(categories);
    }

    /**
     * Cấu hình giới hạn cho các Spinner chọn Giờ (0-23) và Phút (0-59).
     * Lý do: Đảm bảo người dùng không thể nhập thời gian vô lý như "25 giờ 61 phút".
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
     * Ràng buộc TextField Giá khởi điểm chỉ cho phép nhập số.
     * Lý do: Ngăn chặn lỗi NumberFormatException khi chuyển đổi String sang Double/Integer lúc lưu DB.
     */
    private void setupPriceFormatter() {
        txtStartPrice.textProperty().addListener((observable, oldValue, newValue) -> {
            // Nếu code đang tự động format thì bỏ qua
            if (isFormattingPrice) return;

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
                // Dùng Platform.runLater để yêu cầu JavaFX: "Đợi thao tác xóa/gõ hiện tại hoàn tất rồi mới dời con trỏ nhé"
                Platform.runLater(() -> {
                    txtStartPrice.positionCaret(txtStartPrice.getText().length());
                });

            } catch (Exception e) {
                // Lỡ có lỗi không xác định, khôi phục lại giá trị cũ an toàn
                txtStartPrice.setText(oldValue);
                System.err.println("Lỗi format tiền: " + e.getMessage());
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
        int remaining = MAX_IMAGES - uploadedImagePaths.size();
        if (remaining <= 0) {
            AlertUtils.showWarning("Đã đủ ảnh",
                    "Bạn chỉ được tải lên tối đa " + MAX_IMAGES + " ảnh.");
            return;
        }
        // Sử dụng FileChooser mặc định của hệ điều hành để chọn file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm (còn " + remaining + " ảnh)");
        // Lọc chỉ cho phép chọn file ảnh
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        // Lấy Stage (cửa sổ hiện tại) để hiển thị hộp thoại chọn file
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null || selectedFiles.isEmpty()) return;
            // Chỉ lấy đủ số ảnh còn được phép
        int canAdd = Math.min(selectedFiles.size(), remaining);
        for (int i = 0; i < canAdd; i++) {
            addImageThumbnail(selectedFiles.get(i).toURI().toString());
        }

        if (selectedFiles.size() > canAdd) {
            AlertUtils.showWarning("Giới hạn ảnh",
                    "Chỉ " + canAdd + " ảnh đầu được thêm. Đã đạt giới hạn " + MAX_IMAGES + " ảnh.");
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
        btnRemove.setOnAction(e -> removeImage(wrapper));

        imageFlowPane.getChildren().add(wrapper);
    }
    private Button buildRemoveButton() {
        Button btn = new Button("✕");
        btn.setStyle(
                "-fx-background-color: #e53935; -fx-text-fill: white; " +
                        "-fx-font-size: 9px; -fx-padding: 1 4; " +
                        "-fx-background-radius: 10; -fx-cursor: hand;"
        );
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
    private void removeImage(StackPane wrapper) {
        int index = imageFlowPane.getChildren().indexOf(wrapper);
        if (index >= 0 && index < uploadedImagePaths.size()) {
            uploadedImagePaths.remove(index);
            imageFlowPane.getChildren().remove(wrapper);
        }
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
            Items   newItem    = buildItem();
            Auction newAuction = buildAuction(newItem);
            Map<String, byte[]> imageDataMap = buildImageDataMap(newItem.getId());

            AuctionClient client = SessionManager.getInstance().getAuctionClient();
            if (client == null) {
                AlertUtils.showError("Lỗi kết nối", "Không tìm thấy kết nối tới Server.");
                setSubmitLoading(false);
                return;
            }

            client.requestCreateAuction(newItem, newAuction, imageDataMap);

        } catch (Exception e) {
            System.err.println("ADD ITEM: Lỗi đóng gói dữ liệu - " + e.getMessage());
            AlertUtils.showError("Lỗi", "Không thể đọc hoặc nén dữ liệu ảnh. Vui lòng thử lại.");
            setSubmitLoading(false);
        }
    }
    private Items buildItem() {
        String itemId       = UUID.randomUUID().toString();
        String name         = txtProductName.getText().trim();
        String category     = cbCategory.getValue();
        String description  = txtProductDescription.getText().trim();
        RegularUser seller  = (RegularUser) SessionManager.getInstance().getCurrentUser();

        return ItemFactory.createItem(category, itemId, name, description, seller);
    }

    private Auction buildAuction(Items item) {
        String auctionId  = UUID.randomUUID().toString();
        long   startPrice = parsedPrice();
        LocalDateTime start = buildLocalDateTime(dpStartDate, spStartHour, spStartMin);
        LocalDateTime end   = buildLocalDateTime(dpEndDate,   spEndHour,   spEndMin);

        Auction auction = new Auction(auctionId, item, start, end, startPrice);
        auction.setStatus(AuctionStatus.PENDING); // Chờ Admin duyệt
        return auction;
    }

    /**
     * Đọc từng file ảnh thành byte[], đặt tên file duy nhất có chứa UUID item.
     */
    private Map<String, byte[]> buildImageDataMap(String itemId) throws Exception {
        Map<String, byte[]> imageDataMap = new LinkedHashMap<>();
        String shortId = itemId.substring(0, 8);

        for (int i = 0; i < uploadedImagePaths.size(); i++) {
            String uriPath  = uploadedImagePaths.get(i);
            byte[] bytes    = Files.readAllBytes(Paths.get(URI.create(uriPath)));
            String fileName = "item_" + shortId + "_" + i + ".jpg";
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
     * @return true nếu tất cả hợp lệ, false nếu có lỗi.
     */
    private boolean validateInputs() {
        // Kiểm tra bỏ trống
        if (txtProductName.getText().trim().isEmpty() || cbCategory.getValue() == null || txtStartPrice.getText().trim().isEmpty()) {
            AlertUtils.showError("Lỗi Nhập Liệu", "Vui lòng điền đầy đủ các trường bắt buộc (*).");
            return false;
        }

        // Kiểm tra thời gian
        if (dpStartDate.getValue() == null || dpEndDate.getValue() == null) {
            AlertUtils.showError("Lỗi Nhập Liệu", "Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc.");
            return false;
        }

        LocalDateTime start = LocalDateTime.of(dpStartDate.getValue(), LocalTime.of(spStartHour.getValue(), spStartMin.getValue()));
        LocalDateTime end = LocalDateTime.of(dpEndDate.getValue(), LocalTime.of(spEndHour.getValue(), spEndMin.getValue()));

        if (start.isBefore(LocalDateTime.now())) {
            AlertUtils.showError("Cảnh Báo Thời Gian", "Thời gian bắt đầu không được nằm trong quá khứ.");
            return false;
        }

        if (end.isBefore(start) || end.isEqual(start)) {
            AlertUtils.showError("Cảnh Báo Thời Gian", "Thời gian kết thúc phải diễn ra SAU thời gian bắt đầu.");
            return false;
        }

        // Kiểm tra có ít nhất 1 ảnh (Tùy chọn, tùy vào rule của em)
        if (uploadedImagePaths.isEmpty()) {
            AlertUtils.showError("Thiếu Ảnh", "Vui lòng tải lên ít nhất một ảnh sản phẩm.");
            return false;
        }

        return true;
    }
    private LocalDateTime buildLocalDateTime(DatePicker dp,
                                             Spinner<Integer> hourSpinner,
                                             Spinner<Integer> minSpinner) {
        return LocalDateTime.of(
                dp.getValue(),
                LocalTime.of(hourSpinner.getValue(), minSpinner.getValue())
        );
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
        btnSubmit.setText(isLoading ? "Đang tải lên..." : "Thêm sản phẩm");
    }

    /**
     * Đóng cửa sổ (Stage) hiện tại.
     */
    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
    @Override
    public void onCreateAuctionResult(boolean isSuccess, String message) {
        Platform.runLater(() -> {
            btnSubmit.setDisable(false);
            btnSubmit.setText("Thêm sản phẩm");

            if (isSuccess) {
               AlertUtils.showSuccess("Thành công", message);
                // Đóng cửa sổ (Tương đương gọi nút Cancel)
                btnCancel.fire();
            } else {
                AlertUtils.showError("Thất bại", message);
            }
        });
    }
}

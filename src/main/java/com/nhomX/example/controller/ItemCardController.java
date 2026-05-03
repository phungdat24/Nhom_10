package com.nhomX.example.controller;

import com.nhomX.example.model.Items;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;

public class ItemCardController {
    @FXML
    private Label lblTimeLeft;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblItemName;
    @FXML
    private ImageView imgProduct;
    @FXML
    private ImageView itemImageView;


    private Items currentItem;

    // Hàm này sẽ được MainDashboardController gọi để nhồi dữ liệu vào
    public void setItemData(Items item) {
        this.currentItem = item;

        lblItemName.setText(item.getTitle());
        lblCurrentPrice.setText("Giá: " + item.getCurrentPrice() + " VNĐ");
        // Giả sử thẻ ImageView trên giao diện của bạn tên là itemImageView
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            try {
                Image img = new Image(getClass().getResourceAsStream(item.getImagePath()));
                itemImageView.setImage(img); // Đưa ảnh lên giao diện
            } catch (Exception e) {
                System.out.println("❌ Lỗi load ảnh cho: " + item.getTitle() + ". Đường dẫn: "
                        + item.getImagePath());
            }
        }

        // TODO: Logic set ảnh dựa theo item.getImagePath()
        // TODO: Logic đếm ngược thời gian
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        // Khi người dùng bấm "Đấu giá" ở ĐÚNG ô sản phẩm này
        System.out.println("Đang mở cửa sổ đấu giá cho: " + currentItem.getTitle());
        // Lấy ID sản phẩm: currentItem.getId() để gửi qua Socket cho Member 2
    }

    @FXML
    void handleDetailAction(ActionEvent event) {
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nhomX/example/fxml/ItemDetail.fxml"));
            Parent root = loader.load();

            ItemDetailController detailController = loader.getController();
            detailController.setItemData(this.currentItem);

            // 3. Chuyển Scene
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        }catch (IOException e){
            e.printStackTrace();
            System.out.println("KHÔNG THỂ MỞ TRANG CHI TIẾT SẢN PHẨM!" + e.getMessage());
        }
        System.out.println("Information: " + currentItem.getDescription());
    }
}

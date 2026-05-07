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
         // Xử lý hình ảnh (Hỗ trợ ảnh trống và nhiều ảnh Slideshow)
        String dbImagePath = item.getImagePath();

        // 1. Nếu ảnh rỗng -> Hiện No Image
        if (dbImagePath == null || dbImagePath.trim().isEmpty()) {
            try {
                itemImageView.setImage(new Image(
                        getClass().getResourceAsStream("/com/nhomX/example/images/no_image.png")));
            } catch (Exception e) {
                System.err.println("❌ Không tìm thấy file no_image.png trong thư mục!");
            }
        }
        // 2. Nếu có dữ liệu ảnh -> Cắt chuỗi lấy ảnh đầu tiên
        else {
            try {
                 String firstImage = dbImagePath.split(",")[0].trim();
                itemImageView.setImage(new Image(getClass().getResourceAsStream(firstImage)));
            } catch (Exception e) {
                // Nếu file ảnh bị sai đường dẫn, fallback về No Image cho an toàn
                System.err.println(
                        "❌ Lỗi load ảnh cho: " + item.getTitle() + ". Đường dẫn: " + dbImagePath);
                itemImageView.setImage(new Image(
                        getClass().getResourceAsStream("/com/nhomX/example/images/no_image.png")));
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
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/nhomX/example/fxml/ItemDetail.fxml"));
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

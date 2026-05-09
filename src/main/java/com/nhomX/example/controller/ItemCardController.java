package com.nhomX.example.controller;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
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
import java.util.List;

public class ItemCardController {
    @FXML
    // Thời gian
    private Label lblTimeLeft;
    // Gia hiện tại
    @FXML
    private Label lblCurrentPrice;
    // Tên vật phẩm
    @FXML
    private Label lblItemName;
    // Anhr mô tả
    @FXML
    private ImageView imgProduct;

    @FXML
    private ImageView itemImageView;


    private Auction currentAuction;

    // Hàm này sẽ được MainDashboardController gọi để nhồi dữ liệu vào
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        Items item = auction.getItem();
        lblItemName.setText(item.getTitle());
        lblCurrentPrice.setText("Giá: " + auction.getHighestBid() + " VNĐ");
         // Xử lý hình ảnh (Hỗ trợ ảnh trống và nhiều ảnh Slideshow)
        List<ItemImage> images = item.getImages();

        if (images == null || images.isEmpty()|| images.get(0).getImagePath() == null || images.get(0).getImagePath().trim().isEmpty()) {
            try {
                itemImageView.setImage(new Image(
                        getClass().getResourceAsStream("/com/nhomX/example/images/no_image.png")));
            } catch (Exception e) {
                System.err.println("❌ Không tìm thấy file no_image.png trong thư mục!");
            }
        }
        // 2. Nếu có dữ liệu ảnh -> Cắt chuỗi lấy ảnh đầu tiên
        else {
            String firstImage = images.get(0).getImagePath().trim();
            try {
                itemImageView.setImage(new Image(getClass().getResourceAsStream(firstImage)));
            } catch (Exception e) {
                // Nếu file ảnh bị sai đường dẫn, fallback về No Image cho an toàn
                System.err.println(
                        "❌ Lỗi load ảnh cho: " + item.getTitle() + ". Đường dẫn: " + firstImage);
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
        System.out.println("Đang mở cửa sổ đấu giá cho: " + currentAuction.getId());
        // Lấy ID sản phẩm: currentItem.getId() để gửi qua Socket cho Member 2
    }

    @FXML
    void handleDetailAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/nhomX/example/fxml/ItemDetail.fxml"));
            Parent root = loader.load();
            
            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(this.currentAuction);

            // 3. Chuyển Scene
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        }catch (IOException e){
            e.printStackTrace();
            System.out.println("KHÔNG THỂ MỞ TRANG CHI TIẾT SẢN PHẨM!" + e.getMessage());
        }
        System.out.println("Information: " + currentAuction.getItem().getDescription());
    }
}

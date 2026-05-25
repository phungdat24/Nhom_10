package com.nhomX.example.controller.admin;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.utils.CurrencyFormatter;
import com.nhomX.example.utils.ImageLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import java.util.List;


public class AdminPendingProductCardController {
    // 1. ÁNH XẠ CÁC THÀNH PHẦN GIAO DIỆN TỪ FXML
    @FXML private ImageView imgProduct;
    @FXML private Label lblProductName;
    @FXML private Label lblSellerName;
    @FXML private Label lblPriceValue;

    @FXML private Button    btnApprove;
    @FXML private Button btnReject;

    // 2. BIẾN LƯU TRỮ TRẠNG THÁI (STATE)
    // Lưu lại toàn bộ dữ liệu của phiên đấu giá này để dùng khi bấm nút Duyệt/Từ chối
    private Auction currentAuction;

    /**
     * HÀM BƠM DỮ LIỆU (DATA BINDING)
     * Hàm này sẽ được AdminProductManagementController gọi khi dùng vòng lặp tạo thẻ.
     */
    public void setData(Auction auction) {
        this.currentAuction = auction;

        // Bơm Text (Tên, Giá)
        lblProductName.setText(auction.getItem().getTitle());
        lblPriceValue.setText(CurrencyFormatter.formatVND(auction.getStartingPrice()));

        // Trích xuất tên người bán (Seller) an toàn
        if (auction.getItem().getSeller() != null) {
            lblSellerName.setText("Người bán: " + auction.getItem().getSeller().getFullName());
        } else {
            lblSellerName.setText("Người bán: Không xác định");
        }

        // Bơm Ảnh (Tận dụng lại công cụ ImageLoader cực xịn em đã làm ở các buổi trước)
        List<ItemImage> images = auction.getItem().getImages();
        if (images != null && !images.isEmpty() && images.get(0).getImagePath() != null) {
            String fileName = images.get(0).getImagePath().trim();
            ImageLoader.loadAsync(fileName, imgProduct);
        } else {
            ImageLoader.loadAsync(null, imgProduct); // Tự động load ảnh placeholder
        }
    }

    /**
     * HÀNH ĐỘNG: TỪ CHỐI SẢN PHẨM
     */
    @FXML
    public void handleRejected(ActionEvent event) {
        if (currentAuction == null) return;

        System.out.println("ADMIN: Đang gửi lệnh TỪ CHỐI sản phẩm " + currentAuction.getId());
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // Gọi hàm rejectAuction mà ta đã thiết kế ở Networking
            client.rejectAuction(currentAuction.getId());
        }
    }

    /**
     * HÀNH ĐỘNG: DUYỆT SẢN PHẨM
     */
    @FXML
    public void handleApproved(ActionEvent event) {
        if (currentAuction == null) return;

        System.out.println("ADMIN: Đang gửi lệnh DUYỆT sản phẩm " + currentAuction.getId());
        AuctionClient client = SessionManager.getInstance().getAuctionClient();
        if (client != null) {
            // Gọi hàm approveAuction mà ta đã thiết kế ở Networking
            client.approveAuction(currentAuction.getId());
        }
    }
}

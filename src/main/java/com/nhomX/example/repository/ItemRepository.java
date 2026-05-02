package com.nhomX.example.repository;

import java.util.List;

import com.nhomX.example.model.Items;

// Interface quản lý vật phẩm đấu giá:
public interface ItemRepository {
    // Lưu sản phẩm mới:
    void save(Items item);

    // Cập nhật thông tin:
    void update(Items item);

    // Lấy thông tin một Item:
    Items findById(String id);

    // Lấy danh sách tất cả vật phẩm để hiển thị lên trang chủ:
    List<Items> findAll();

    // Lọc sản phẩm theo loại:
    List<Items> findByCategory(String category);

    // Lấy danh sách các món đồ đã hết hạn nhưng chưa đóng phiên
    List<Items> findExpiredOpenItems();

    // Cập nhật trạng thái và người chiến thắng
    boolean updateStatusAndWinner(String itemId, String status, String winnerId);
}

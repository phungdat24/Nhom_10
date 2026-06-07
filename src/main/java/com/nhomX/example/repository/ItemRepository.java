package com.nhomX.example.repository;

import com.nhomX.example.model.Items;

import java.util.List;
// Interface quản lý vật phẩm đấu giá:
public interface ItemRepository {
    /** Lưu sản phẩm mới (kèm ảnh nếu có). */
    void save(Items item);

    /** Cập nhật thông tin sản phẩm và ảnh. */
    void update(Items item);

    /** Xóa sản phẩm theo ID. */
    boolean deleteItemAndAuction(String itemId);

    /** Tìm sản phẩm theo ID. */
    Items findById(String id);

    /** Lấy toàn bộ danh sách sản phẩm. */
    List<Items> findAll();

    /** Lọc sản phẩm theo danh mục. */
    List<Items> findByCategory(String category);

    /**
     * [THÊM MỚI] Lấy tất cả sản phẩm của một Seller cụ thể.
     * Seller cần xem sản phẩm của mình trên màn hình quản lý.
     */
    List<Items> findBySellerId(String sellerId);
}

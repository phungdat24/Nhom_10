package com.nhomX.example.repository;

import com.nhomX.example.model.AutoBidConfig;

import java.util.List;

public interface AutoBidRepository {
    // Lưu một cấu hình đặt tự động mới
    boolean save(AutoBidConfig config);

    // Lấy tất cả cấu hình AutoBid của một Phiên đấu giá cụ thể
    // (Dùng để Server tự động quét khi có người đặt giá)
    List<AutoBidConfig> findByAuctionId(String auctionId);

    // Xóa cấu hình nếu người dùng muốn hủy AutoBid
    void delete(String configId);
    /**
     * [THÊM MỚI] Tìm config của một user trong một phiên cụ thể.
     * Dùng để kiểm tra user đã setup auto-bid chưa trước khi tạo mới.
     */
    AutoBidConfig findByUserAndAuction(String userId, String auctionId);
    /**
     * [THÊM MỚI] Vô hiệu hóa (deactivate) config thay vì xóa,
     * giữ lại lịch sử nhưng không trigger nữa.
     */
    void deactivate(String configId);
}

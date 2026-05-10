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
}

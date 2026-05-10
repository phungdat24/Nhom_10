package com.nhomX.example.repository;

import java.util.List;

import com.nhomX.example.model.Auction;

public interface AuctionRepository {
    // Lưu phiên đấu giá mới vào DB
    void save(Auction auction);

    // Tìm 1 phiên đấu giá theo ID
    Auction findById(String id);

    // Lấy danh sách các phiên đang mở (OPEN / RUNNING) để hiển thị lên Dashboard
    List<Auction> findAllActiveAuctions();

    // [THÊM MỚI] Dành riêng cho AuctionScheduler (Ông bảo vệ chạy ngầm)
    // Nhiệm vụ: Tìm các phiên đã vượt quá thời gian end_time nhưng chưa đóng
    List<Auction> findExpiredOpenAuctions();

    // Cập nhật Trạng thái phiên (Ví dụ: Từ OPEN sang FINISHED)
    void updateStatus(String auctionId, String status);

    // Cập nhật Giá cao nhất và Người đang dẫn đầu (Dùng khi có người đặt giá)
    void updatePriceAndWinner(String auctionId, long newPrice, String winnerId);
}

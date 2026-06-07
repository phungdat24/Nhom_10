package com.nhomX.example.repository;

import java.time.LocalDateTime;
import java.util.List;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.MyAuctionDTO;

public interface AuctionRepository {
    // Lưu phiên đấu giá mới vào DB
    void save(Auction auction);

    // Tìm 1 phiên đấu giá theo ID
    Auction findById(String id);

    // Lấy danh sách các phiên đang mở (OPEN / RUNNING) để hiển thị lên Dashboard
    List<Auction> findAllActiveAuctions();

    // Dành riêng cho AuctionScheduler (Ông bảo vệ chạy ngầm)
    // Nhiệm vụ: Tìm các phiên đã vượt quá thời gian end_time nhưng chưa đóng
    List<Auction> findExpiredOpenAuctions();

    // Cập nhật Trạng thái phiên (Ví dụ: Từ OPEN sang FINISHED)
    void updateStatus(String auctionId, AuctionStatus status);

    // Cập nhật Giá cao nhất và Người đang dẫn đầu (Dùng khi có người đặt giá)
    void updateHighestBidAndWinner(String auctionId, long newPrice, String winnerId);
    // Cập nhật thời gian kết thúc – dùng cho Anti-sniping.

    void updateEndTime(String auctionId, LocalDateTime newEndTime);

    // Lấy tất cả phiên theo seller (Seller xem phiên của mình).
    List<Auction> findBySellerId(String sellerId);

    // [MỚI]: Lấy danh sách phiên người dùng đã tham gia kèm trạng thái DTO
    List<MyAuctionDTO> getMyAuctions(String userId);

    List<Auction> getEndingSoonAuctions(int limit);

    List<Auction> getTrendingAuctions(int limit);

    int countActiveAuctions();

    int countEndingSoonAuctions();

    List<Auction> findAuctionsByStatus(AuctionStatus auctionStatus);

    boolean updateAuctionStatus(Auction auction);

    // Tat toan phien dau gia khi het gio: cong tien cho seller va cap nhat trang thai.
    boolean settleAuctionPayment(String auctionId);

    // [THÊM MỚI] Dành riêng cho Scheduler quét các phiên chuẩn bị mở
    List<Auction> findReadyToOpenAuctions();

    List<Auction> findLiveAuctions();
    boolean cancelAuction(String auctionId);
}


package com.nhomX.example.model;

/**
 * Interface Observer cho hệ thống đấu giá.
 * Áp dụng: Observer Pattern – yêu cầu bắt buộc (Realtime Update 0.5đ).
 *
 * Các lớp muốn nhận thông báo khi có bid mới implement interface này:
 * - ClientHandler (server-side): gửi thông báo qua socket tới client
 * - AuctionController (client-side): cập nhật UI JavaFX
 */


public interface AuctionObserver {
    /**
     * Được gọi khi có bid mới hợp lệ được đặt trong phiên.
     *
     * @param auction Phiên đấu giá vừa có bid mới (chứa giá cao nhất mới nhất).
     * @param newBid  Giao dịch bid vừa được thực hiện (null nếu chỉ thay đổi thời gian).
     */
    void onBidPlaced(Auction auction, BidTransaction newBid);

    /**
     * Được gọi khi phiên đấu giá kết thúc (hết giờ hoặc bị hủy).
     *
     * @param auction Phiên đấu giá vừa kết thúc.
     */
    void onAuctionClosed(Auction auction);
}

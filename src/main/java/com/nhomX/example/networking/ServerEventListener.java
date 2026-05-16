package com.nhomX.example.networking;

import com.nhomX.example.model.*;

import java.util.List;

public interface ServerEventListener {
    // Sự kiện đăng nhập
    default void onLoginResult (boolean isSuccess, String message, User userData) {};
    // Sự kiện cập nhật Realtime
    default void onHighestBidUpdated(String auctionId, long newPrice, String bidderName) {};
    // Sự kiên nhận danh sách Items:
    default void onAuctionsReceived(List<Auction> auctions) {};
    // Thêm sự kiện phản hồi Đăng ký
    default void onRegisterResult(boolean isSuccess, String message) {}
    /** Kết quả đặt giá (BID_SUCCESS / BID_FAIL). */
    default void onBidResult(boolean isSuccess, String message) {}

    /**
     * Phiên đấu giá đã kết thúc – cập nhật UI hiển thị trạng thái FINISHED/CANCELED.
     *
     * @param auctionId ID phiên vừa đóng.
     * @param winnerId  ID người thắng, null nếu không có ai đặt giá.
     */
    default void onAuctionClosed(String auctionId, String winnerId) {}

    /** Mất kết nối với Server – hiển thị popup hoặc chuyển màn hình. */
    default void onConnectionLost(String reason) {}
    default void onBidHistoryReceived(List<BidTransaction> history) {}
    // Nghe ngóng OTP:
    default void onShowOtpDialog() {}
    default void onDashboardDataReceived(List<Auction> endingSoon, List<Auction> trending) {}

    default void onMyAuctionsReceived(List<com.nhomX.example.model.MyAuctionDTO> myAuctionsList) {}
}

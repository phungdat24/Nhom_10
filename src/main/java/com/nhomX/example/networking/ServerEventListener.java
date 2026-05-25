package com.nhomX.example.networking;

import com.nhomX.example.model.*;

import java.util.List;
import java.util.Map;

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

    default void onDashboardDataReceived(Map<String, Integer> stats, List<Auction> endingSoon, List<Auction> trending) {}

    default void onMyAuctionsReceived(List<MyAuctionDTO> myAuctionsList) {}

    // Lắng nghe sự kiện thay đổi số người Online
    default void onOnlineCountUpdated(int onlineCount) {}

    default void onForgotPasswordResult(boolean isSuccess, String responseMsg){}
    // Lấy danh sách bán
    default void onSellerAuctionsReceived(List<Auction> sellerAuctions){};
    // Lắng nghe kết quả tạo phiên đấu giá
    default void onCreateAuctionResult(boolean isSuccess, String message) {}
    default void onPendingAuctionsReceived(List<Auction> pendingAuctions) {}
    default void onAdminActionCompleted(boolean isSuccess, String message) {}
    default void onNewPendingAuctionReceived(Auction newAuction) {}
    // SỰ KIỆN GIAO DỊCH TÀI CHÍNH
    // ==========================================
    /**
     * Lắng nghe kết quả nạp tiền từ Server.
     * @param isSuccess Thành công hay thất bại.
     * @param newBalance Số dư mới nhất (nếu thành công).
     */
    default void onDepositResult(boolean isSuccess, long newBalance) {}
    default void onAuctionApproved(Auction updatedAuction) {}
}

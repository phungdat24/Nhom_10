package com.nhomX.example.model;

public enum AuctionStatus {
    // Chờ ADMIN xét duyệt:
    PENDING,
    // Da duoc Admin duyet nhung chua den gio bat dau
    UP_COMING,
    // Phiên vừa được duyệt, chưa có ai đấu giá
    OPEN,
    // Đang diễn ra, có ít nhất 1 bid
    RUNNING,
    // Hết giờ, xác định người thắng
    FINISHED,
    // Người thắng đã thanh toán
    PAID,
    //Đã hủy(kHÔNG CÓ BID HOẶC ADMIN HỦY:
    CANCELED,
}

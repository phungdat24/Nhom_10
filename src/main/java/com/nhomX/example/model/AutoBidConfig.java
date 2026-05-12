package com.nhomX.example.model;

import java.time.LocalDateTime;

public class AutoBidConfig extends Entity {

    // Số tiền tối đa mà người dùng sẵn sàng trả cho món hàng này
    private long maxLimit;

    // Bước giá tự động cộng thêm mỗi khi bị người khác vượt mặt
    private long increment;

    // Thời điểm thiết lập cấu hình
    private LocalDateTime createdAt;

    // Tham chiếu đối tượng: Ai là người cài đặt?
    private RegularUser bidder;

    // Tham chiếu đối tượng: Cài đặt cho phiên đấu giá nào?
    private Auction auction;

    // Biến cờ (flag) để bật/tắt cấu hình này
    private boolean active;

    // 1. Hàm tạo rỗng
    public AutoBidConfig() {
        super();
        this.active = true; // Mặc định khi vừa tạo là được kích hoạt
    }

    // 2. Hàm tạo đầy đủ tham số
    public AutoBidConfig(String id, long maxLimit, long increment, RegularUser bidder, Auction auction) {
        super(id);
        this.setMaxLimit(maxLimit);
        this.setIncrement(increment);
        // Tự động lấy giờ hệ thống
        this.createdAt = LocalDateTime.now();
        this.bidder = bidder;
        this.auction = auction;
        this.active = true;
    }
    // CÁC HÀM NGHIỆP VỤ (BUSINESS LOGIC)

    /**
     * Hàm được định nghĩa trong UML: Kiểm tra cấu hình có đang hoạt động hợp lệ không.
     */
    public boolean isActive() {
        // Cấu hình chỉ có tác dụng khi:
        // 1. Nó chưa bị tắt (active == true)
        // 2. Phiên đấu giá đó vẫn còn đang mở cửa nhận Bid
        return this.active && this.auction.canAcceptBids();
    }

    /**
     * Hàm vô hiệu hóa cấu hình (Ví dụ: Khi user tự tắt, hoặc khi đã chạm ngưỡng maxLimit)
     */
    public void deactivate() {
        this.active = false;
    }
    /**
     * Tính toán mức giá auto-bid tiếp theo.
     * Trả về currentPrice + increment, nhưng không vượt maxLimit.
     * Trả về -1 nếu không thể đặt thêm (đã chạm maxLimit).
     *
     * @param currentPrice Giá cao nhất hiện tại của phiên.
     * @return Mức giá đề xuất, hoặc -1 nếu đã hết ngưỡng.
     */
    public long computeNextBid(long currentPrice) {
        long nextBid = currentPrice + this.increment;
        if (nextBid > this.maxLimit) {
            // Nếu chính maxLimit vẫn cao hơn currentPrice thì đặt thẳng maxLimit
            if (this.maxLimit > currentPrice) {
                return this.maxLimit;
            }
            return -1; // Đã vượt ngưỡng, không thể đặt thêm
        }
        return nextBid;
    }


    // GETTERS VÀ SETTERS CÓ KIỂM TRA ĐIỀU KIỆN

    public long getMaxLimit() { return maxLimit; }

    public void setMaxLimit(long maxLimit) {
        if (maxLimit <= 0) {
            throw new IllegalArgumentException("Giới hạn tối đa (Max Limit) phải lớn hơn 0!");
        }
        this.maxLimit = maxLimit;
    }

    public long getIncrement() {
        return increment; }

    public void setIncrement(long increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("Bước giá (Increment) phải lớn hơn 0!");
        }
        this.increment = increment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; }

    public RegularUser getBidder() {
        return bidder; }

    public void setBidder(RegularUser bidder) {
        this.bidder = bidder; }

    public Auction getAuction() {
        return auction; }

    public void setAuction(Auction auction) {
        this.auction = auction; }

    // Setter cho cờ active trong trường hợp cần load từ Database lên
    public void setActive(boolean active) {
        this.active = active; }
}

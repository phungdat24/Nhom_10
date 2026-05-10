package com.nhomX.example.model;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {

    // Thời gian đấu giá
    private LocalDateTime bidTime;
    // ID của vật phẩm đặt giá
    private RegularUser bidder;
    // Gía đấu
    private long amount;
    // id của lượt trả giá
    private Auction auction;

    public BidTransaction() {
        // Hàm tạo rỗng để đọc dữ liệu tu db
    }

    public BidTransaction(String id, LocalDateTime bidTime, long amount,RegularUser bidder, Auction auction) {
        super(id);
        this.setAmount(amount);
        this.bidTime = bidTime;
        this.bidder= bidder;
        this.auction =auction;
    }
    /**
     * Kiểm tra tính hợp lệ cơ bản của một giao dịch trả giá.
     * Hàm này được gọi trước khi đẩy gói tin lên mạng hoặc lưu DB.
     */
    public boolean isValid() {
        // Giá phải lớn hơn 0
        if (this.amount <= 0) {
            return false;
        }
        // Giao dịch không thể thiếu Người đặt hoặc Phiên đấu giá
        if (this.bidder == null || this.auction == null) {
            return false;
        }
        // Có thể thêm logic: Kiểm tra xem user có bị ban/cấm không, v.v.
        return true;
    }

    public void setBidTime(LocalDateTime bidTime) {
        this.bidTime = bidTime;
    }

    public void setAmount(long amount) {
        if(amount<0){
            throw new IllegalArgumentException("Số tiền đấu giá không được phép âm!");
        }
        this.amount = amount;

    }

    public void setBidder(RegularUser bidder) {
        this.bidder = bidder;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public RegularUser getBidder() {
        return bidder;
    }

    public Auction getAuction() {
        return auction;
    }

    public LocalDateTime getBidTime() {
        return this.bidTime;
    }

    public long getAmount() {
        return this.amount;
    }
}

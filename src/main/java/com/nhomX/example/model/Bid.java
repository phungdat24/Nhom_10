package com.nhomX.example.model;

import java.time.LocalDateTime;

public class Bid {
    public Bid() {
        // Hàm tạo rỗng để Java cho phép tạo đối tượng Bid trống
    }

    private LocalDateTime bidTime; // thời gian đấu giá
    private String userId; // Người đấu giá
    private String itemId; // id của vật phẩm đặt giá
    private long amount; // Gía đấu
    private String id; // id của lượt trả giá

    public Bid(LocalDateTime bidTime, String userId, String itemId, long amount, String id) {
        this.amount = amount;
        this.bidTime = bidTime;
        this.userId = userId;
        this.itemId = itemId;
        this.id = id;
    }

    public void setBidTime(LocalDateTime bidTime) {
        this.bidTime = bidTime;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getBidTime() {
        return this.bidTime;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getItemId() {
        return this.itemId;
    }

    public long getAmount() {
        return this.amount;
    }

    public String getId() {
        return this.id;
    }
}

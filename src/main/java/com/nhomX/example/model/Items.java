package com.nhomX.example.model;

import java.time.LocalDateTime;

public abstract class Items {
    private String id; // id của món hàng
    private String title; // Tên sản phẩm
    private String description; // Mô tả sản pham
    private int startingPrice; // Gía khởi điểm
    private int currentPrice; // Gía hiện tại
    private LocalDateTime endTime;
    private String sellerId; // Id người đăng bán sản phẩm
    public Items(String id, String title, String sellerId  ){
        this.id = id;
        this.title= title;
        this.sellerId=sellerId;
    }
// getter cho các thuộc tính
    public String getId() {
        return this.id;
    }
    public String getTitle() {
        return this.title;
    }
    public String getDescription() {
        return this.description;
    }
    public int getStartingPrice() {
        return this.startingPrice;
    }
    public int getCurrentPrice() {
        return this.currentPrice;
    }
    public LocalDateTime getEndTime() {
        return this.endTime;
    }
    public String getSellerId() {
        return this.sellerId;
    }
// Setter cho các thuộc tính:
    public void setId(String id) {
        this.id = id;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setStartingPrice(int startinngPrice) {
        this.startingPrice = startinngPrice;
    }
    public void setCurrentPrice(int currentPrice) {
        this.currentPrice = currentPrice;
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

}

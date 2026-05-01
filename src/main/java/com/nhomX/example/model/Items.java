package com.nhomX.example.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public abstract class Items implements Serializable {
    private String id; // id của món hàng
    private String title; // Tên sản phẩm
    private String description; // Mô tả sản pham
    private double startingPrice; // Gía khởi điểm
    private double currentPrice; // Gía hiện tại
    private LocalDateTime endTime;
    private String sellerId; // Id người đăng bán sản phẩm

    public Items() {
        // Hàm tạo rỗng của lớp Cha để cho phép lớp Con khởi tạo trống
    }

    public Items(String id, String title, String sellerId) {
        this.id = id;
        this.title = title;
        this.sellerId = sellerId;
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

    public double getStartingPrice() {
        return this.startingPrice;
    }

    public double getCurrentPrice() {
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

    public void setStartingPrice(double startinngPrice) {
        this.startingPrice = startinngPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

}

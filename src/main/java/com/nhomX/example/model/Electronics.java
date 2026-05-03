package com.nhomX.example.model;

public class Electronics extends Items {
    private String brand; // nhãn hàng
    private int warrantyPeriod;

    public Electronics(String id, String title, String sellerId, String brand) {
        super(id, title, sellerId);
        this.brand = brand;
    }

    // Hàm getter cho các thuộc tính
    public String getBrand() {
        return this.brand;
    }

    public int getWarrantyPeriod() {
        return this.warrantyPeriod;
    }

    // Hàm setter cho các hàm
    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setWarrantyPeriod(int warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod;
    }
}

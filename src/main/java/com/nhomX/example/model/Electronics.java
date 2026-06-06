package com.nhomX.example.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Electronics extends Items {
    private static final Logger logger = LoggerFactory.getLogger(Electronics.class);
    private String brand; // nhãn hàng
    private int warrantyPeriod;

    public Electronics() {
        super();
    }

    public Electronics(String id, String title, String description, RegularUser seller,
            String brand, int warrantyPeriod) {
        super(id, title, description, seller);
        this.brand = brand;
        this.warrantyPeriod = warrantyPeriod;
    }

    @Override
    public String getCategory() {
        return "Electronics";
    }

    @Override
    public void printItemDetails() {
        logger.info("--- CHI TIẾT ĐỒ ĐIỆN TỬ ---");
        logger.info("Tên SP: {}", getTitle());
        logger.info("Thương hiệu: {}", this.brand);
        logger.info("Bảo hành: {} tháng", this.warrantyPeriod);
    }

    @Override
    public boolean validate() {
        // Kiểm tra hợp lệ: Tên không được rỗng, giá >= 0 và bảo hành không được âm
        if (getTitle() == null || getTitle().trim().isEmpty())
            return false;
        if (this.warrantyPeriod < 0)
            return false;
        return true;
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

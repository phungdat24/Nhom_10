package com.nhomX.example.model;

public class Electronics extends Items {
    private String brand; // nhãn hàng
    private int warrantyPeriod;

    public Electronics(){
        super();
    }

    public Electronics(String id, String title,String description, long startingPrice, RegularUser seller, String brand,int warrantyPeriod) {
        super(id, title,description,startingPrice, seller);
        this.brand = brand;
        this.warrantyPeriod = warrantyPeriod;
    }
    @Override
    public String getCategory() {
        return "Electronics";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT ĐỒ ĐIỆN TỬ ---");
        System.out.println("Tên SP: " + getTitle());
        System.out.println("Thương hiệu: " + this.brand);
        System.out.println("Bảo hành: " + this.warrantyPeriod + " tháng");
        System.out.println("Giá khởi điểm: " + getStartingPrice());
    }

    @Override
    public boolean validate() {
        // Kiểm tra hợp lệ: Tên không được rỗng, giá >= 0 và bảo hành không được âm
        if (getTitle() == null || getTitle().trim().isEmpty()) return false;
        if (getStartingPrice() < 0) return false;
        if (this.warrantyPeriod < 0) return false;
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

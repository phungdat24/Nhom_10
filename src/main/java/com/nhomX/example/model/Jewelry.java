package com.nhomX.example.model;

public class Jewelry extends Items {
    private String gemstoneType;

    public Jewelry(){
        super();
    }

    public Jewelry(String id, String title, String description, long startingPrice, RegularUser seller, String gemstoneType) {
        super(id, title, description, startingPrice, seller);
        this.gemstoneType = gemstoneType;
    }

    @Override
    public String getCategory() {
        return "Jewelry";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT TRANG SỨC ---");
        System.out.println("Tên SP: " + this.title);
        System.out.println("Loại đá quý đính kèm: " + this.gemstoneType);
        System.out.println("Giá khởi điểm: " + this.startingPrice);
    }

    @Override
    public boolean validate() {
        if (this.title == null || this.title.trim().isEmpty()) return false;
        if (this.startingPrice < 0) return false;
        return true;
    }

    public void setGemstoneType(String gemstoneType) {
        this.gemstoneType = gemstoneType;
    }

    public String getGemstoneType() {
        return this.gemstoneType;
    }
}

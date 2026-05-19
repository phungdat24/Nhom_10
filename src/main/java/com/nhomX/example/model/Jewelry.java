package com.nhomX.example.model;

public class Jewelry extends Items {
    private String gemstoneType;

    public Jewelry(){
        super();
    }

    public Jewelry(String id, String title, String description, RegularUser seller, String gemstoneType) {
        super(id, title, description, seller);
        this.gemstoneType = gemstoneType;
    }

    @Override
    public String getCategory() {
        return "Jewelry";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT TRANG SỨC ---");
        System.out.println("Tên SP: " + getTitle());
        System.out.println("Loại đá quý đính kèm: " + this.gemstoneType);
    }

    @Override
    public boolean validate() {
        if (getTitle() == null || getTitle().trim().isEmpty()) return false;
        return true;
    }

    public void setGemstoneType(String gemstoneType) {
        this.gemstoneType = gemstoneType;
    }

    public String getGemstoneType() {
        return this.gemstoneType;
    }
}

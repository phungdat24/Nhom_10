package com.nhomX.example.model;

public class GeneralItem extends Items {

    public GeneralItem() {
        super();
    }
    public GeneralItem(String id, String title, String description, long startingPrice,
                       RegularUser seller) {
        super(id, title, description, startingPrice, seller);
    }

    @Override
    public String getCategory() {
        return "General";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT SẢN PHẨM ---");
        System.out.println("Tên SP: " + this.title);
        System.out.println("Mô tả: " + this.description);
        System.out.println("Giá khởi điểm: " + this.startingPrice);
    }

    @Override
    public boolean validate() {
        if (this.title == null || this.title.trim().isEmpty()) return false;
        if (this.startingPrice < 0) return false;
        return true;
    }
}

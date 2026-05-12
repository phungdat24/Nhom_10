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
        System.out.println("Tên SP: " + getTitle());
        System.out.println("Mô tả: " + getDescription());
        System.out.println("Giá khởi điểm: " + getStartingPrice());
    }

    @Override
    public boolean validate() {
        if (getTitle() == null || getTitle().trim().isEmpty()) return false;
        if (getStartingPrice() < 0) return false;
        return true;
    }
}

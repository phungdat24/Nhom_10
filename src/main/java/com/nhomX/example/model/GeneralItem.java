package com.nhomX.example.model;

public class GeneralItem extends Items {

    public GeneralItem() {
        super();
    }
    public GeneralItem(String id, String title, String description, RegularUser seller) {
        super(id, title, description, seller);
    }

    @Override
    public String getCategory() {
        return "GENERALITEM";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT SẢN PHẨM ---");
        System.out.println("Tên SP: " + getTitle());
        System.out.println("Mô tả: " + getDescription());
    }

    @Override
    public boolean validate() {
        if (getTitle() == null || getTitle().trim().isEmpty()) return false;
        return true;
    }
}

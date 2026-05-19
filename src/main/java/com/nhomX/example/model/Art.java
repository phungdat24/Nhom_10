package com.nhomX.example.model;

import java.time.Year;

public class Art extends Items {
    private String artistName;//ten tac gia
    private int yearCreated; // kiểu int chỉ để lưu trữ năm

    public Art(){
        super();
    }

    public Art(String id, String title, String description, RegularUser seller, String artistName, int yearCreated) {
        super(id, title, description, seller);
        this.artistName = artistName;
        this.yearCreated = yearCreated;
    }

    @Override
    public String getCategory() {
        return "Art";
    }

    @Override
    public void printItemDetails() {
        System.out.println("--- CHI TIẾT TÁC PHẨM NGHỆ THUẬT ---");
        System.out.println("Tác phẩm: " + getTitle());
        System.out.println("Tác giả: " + this.artistName);
        System.out.println("Năm sáng tác: " + this.yearCreated);
        System.out.println("Mô tả: " + getDescription());
    }

    @Override
    public boolean validate() {
        if (getTitle()  == null || getTitle().trim().isEmpty()) return false;
        // Năm sáng tác không được lớn hơn năm hiện tại
        if (this.yearCreated > Year.now().getValue()) return false;
        return true;
    }

    // Hàm getter:
    public String getArtistName() {
        return artistName;
    }
    public int getYearCreated() {
        return this.yearCreated;
    }
    // hàm setter:
    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }
    public void setYearCreated(int yearCreated) {
        this.yearCreated = yearCreated;
    }
}

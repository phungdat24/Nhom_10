package com.nhomX.example.model;

public class Art extends Items {
    private String artistName;
    private int yearCreated; // kiểu int chỉ để lưu trữ năm
    public Art(String id, String title, String sellerId,String artistName){
        super(id, title, sellerId);
        this.artistName =artistName;
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

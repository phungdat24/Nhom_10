package com.nhomX.example.model;

public class Jewelry extends Items{
    private String gemstoneType;
    public Jewelry(String id, String title, String sellerId, String gemstoneType){
        super(id, title, sellerId);
        this.gemstoneType = gemstoneType;
    }
    public void setGemstoneType(String gemstoneType) {
        this.gemstoneType = gemstoneType;
    }

    public String getGemstoneType() {
        return this.gemstoneType;
    }
}

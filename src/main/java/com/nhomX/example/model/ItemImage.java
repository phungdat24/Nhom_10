package com.nhomX.example.model;

public class ItemImage extends Entity {
    // Chỉ cần khai báo những thuộc tính đặc thù của riêng ảnh
    private String imagePath;

    public ItemImage() {}

    public ItemImage(String id, String imagePath) {
        super(id);
        this.imagePath = imagePath;
    }
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}

package com.nhomX.example.model;

public class ItemImage extends Entity {
    // Chỉ cần khai báo những thuộc tính đặc thù của riêng ảnh
    private String imagePath;
    private String itemId; //  Thêm khóa ngoại liên kết với Items

    public ItemImage() {
        super();
    }

    public ItemImage(String id, String imagePath, String itemId) {
        super(id);
        this.imagePath = imagePath;
        this.itemId=itemId;
    }
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
}

package com.nhomX.example.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Items extends Entity {
    // Tên sản phẩm:
    protected String title;
    // Mô tả sản phẩm:
    protected String description;
    // Giá khởi tạo
    protected long startingPrice;
    // Link ảnh:
    protected List<ItemImage> images = new ArrayList<>();
    // Người bán:
    protected  RegularUser seller;

    public Items() {
        super();
    }

    public Items(String id, String title, String description, long startingPrice, RegularUser seller) {
        // Truền id cho lớp cha:
        super(id);
        this.title = title;
        this.images= new ArrayList<>();
        this.seller=seller;
        this.setStartingPrice(startingPrice);
        this.description=description;
    }

    public abstract boolean validate();

    public abstract void printItemDetails();

    public abstract String getCategory();

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public long getStartingPrice() {
        return this.startingPrice;
    }

    public List<ItemImage> getImages() {
        return images;
    }

    public RegularUser getSeller(){
        return this.seller;
    }

    // Setter cho các thuộc tính:

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(long startingPrice) {
        if (startingPrice < 0) {
            throw new IllegalArgumentException("Giá khởi điểm không được phép nhỏ hơn 0!");
        }
        this.startingPrice = startingPrice;
    }

    public void setSeller(RegularUser seller){
        this.seller=seller;
    }

    public void setImages(List<ItemImage> images) {
        this.images = images;
    }

    // Hàm tiện ích để thêm nhanh 1 ảnh
    public void addImage(ItemImage image) {
        if (this.images == null) {
            this.images = new ArrayList<>();
        }
        this.images.add(image);
    }

}

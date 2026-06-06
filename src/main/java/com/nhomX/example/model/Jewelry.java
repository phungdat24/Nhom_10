package com.nhomX.example.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jewelry extends Items {
    private static final Logger logger = LoggerFactory.getLogger(Jewelry.class);
    private String gemstoneType;

    public Jewelry() {
        super();
    }

    public Jewelry(String id, String title, String description, RegularUser seller,
            String gemstoneType) {
        super(id, title, description, seller);
        this.gemstoneType = gemstoneType;
    }

    @Override
    public String getCategory() {
        return "Jewelry";
    }

    @Override
    public void printItemDetails() {
        logger.info("--- CHI TIẾT TRANG SỨC ---");
        logger.info("Tên SP: {}", getTitle());
        logger.info("Loại đá quý đính kèm: {}", this.gemstoneType);
    }

    @Override
    public boolean validate() {
        if (getTitle() == null || getTitle().trim().isEmpty())
            return false;
        return true;
    }

    public void setGemstoneType(String gemstoneType) {
        this.gemstoneType = gemstoneType;
    }

    public String getGemstoneType() {
        return this.gemstoneType;
    }
}

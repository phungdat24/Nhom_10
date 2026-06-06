package com.nhomX.example.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneralItem extends Items {
    private static final Logger logger = LoggerFactory.getLogger(GeneralItem.class);

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
        logger.info("--- CHI TIẾT SẢN PHẨM ---");
        logger.info("Tên SP: {}", getTitle());
        logger.info("Mô tả: {}", getDescription());
    }

    @Override
    public boolean validate() {
        if (getTitle() == null || getTitle().trim().isEmpty())
            return false;
        return true;
    }
}

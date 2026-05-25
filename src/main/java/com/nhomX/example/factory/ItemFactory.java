package com.nhomX.example.factory;

import com.nhomX.example.model.*;

public class ItemFactory {
    /**
     * Factory Method: Sản xuất đối tượng Items dựa trên phân loại (Category)
     * Lưu ý: Giá khởi điểm (startingPrice) KHÔNG nằm ở đây.
     */
    public static Items createItem(String category, String id, String title, String description, User seller) {
        // Ép kiểu (Cast) an toàn từ User sang RegularUser.
        // (Trong hệ thống của chúng ta, Admin không bán hàng nên seller chắc chắn là RegularUser)
        RegularUser regularSeller = (seller instanceof RegularUser) ? (RegularUser) seller : null;
        // Fallback an toàn nếu database bị null category
        if (category == null || category.trim().isEmpty()) {
            return new GeneralItem(id, title, description, regularSeller);
        }

        switch (category.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(id, title, description, regularSeller, null, 0);
            // brand và warrantyPeriod có thể set sau thông qua setter
            case "JEWELRY":
                return new Jewelry(id, title, description, regularSeller, null);
            // gemstoneType có thể set sau
            case "ART":
                return new Art(id, title, description, regularSeller, null, 0);
            // artistName và yearCreated có thể set sau
            case "GENERALITEM":
            default:
                // Nếu gặp loại hàng lạ chưa được định nghĩa class, mặc định đưa về GeneralItem
                return new GeneralItem(id, title, description, regularSeller);
        }
    }
}

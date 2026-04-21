package com.nhomX.example;

import java.time.LocalDateTime;

import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;

public class DatabaseTest {
  public static void main(String[] args) {
    System.out.println("=== BẮT ĐẦU TEST DATABASE ===");

    // 1. Khởi tạo repository (Sẽ tự động kết nối SQLite và tạo bảng nếu chưa có)
    ItemRepository itemRepo = new ItemRepositoryImpl();

    // 2. Tạo một sản phẩm giả lập để test
    GeneralItem newItem = new GeneralItem("ITEM-001", "Laptop Gaming Cũ", "SELLER-999");
    newItem.setDescription("Máy còn mới 99%, bao test 7 ngày.");
    newItem.setStartingPrice(12000000.0);
    newItem.setCurrentPrice(12000000.0);
    newItem.setEndTime(LocalDateTime.now().plusDays(3)); // Đấu giá kết thúc sau 3 ngày

    // 3. Test tính năng Lưu (Insert)
    System.out.println("\n⏳ Đang lưu sản phẩm vào database...");
    itemRepo.save(newItem);

    // 4. Test tính năng Tìm kiếm (Select)
    System.out.println("\n🔍 Đang tìm kiếm sản phẩm ITEM-001 trong DB...");
    Items retrievedItem = itemRepo.findById("ITEM-001");

    // 5. In kết quả ra màn hình
    if (retrievedItem != null) {
      System.out.println("🎉 TÌM THẤY SẢN PHẨM TRONG DATABASE!");
      System.out.println("- ID: " + retrievedItem.getId());
      System.out.println("- Tên SP: " + retrievedItem.getTitle());
      System.out.println("- Mức giá: " + retrievedItem.getCurrentPrice() + " VNĐ");
      System.out.println("- Mô tả: " + retrievedItem.getDescription());
    } else {
      System.out.println("❌ Không tìm thấy sản phẩm này.");
    }

    System.out.println("=== KẾT THÚC TEST ===");
  }
}

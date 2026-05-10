package com.nhomX.example;

import java.sql.Connection;
import java.sql.Statement;

import com.nhomX.example.utils.DatabaseConnection;

public class DatabaseTest {
  public static void main(String[] args) {
    // Chuỗi SQL tổng hợp để nạp dữ liệu mẫu
    // Chú ý: Thứ tự chèn rất quan trọng để không bị lỗi Khóa ngoại (Foreign Key)
    String sql =
        // 1. Tạo người dùng mẫu (Người bán & Người mua)
        "INSERT INTO users (id, username, password, fullname, balance, role) VALUES "
            + "('U001', 'seller1', '123', 'Nguyen Van Ban', 0, 'USER'), "
            + "('U002', 'buyer1', '123', 'Tran Thi Mua', 5000000, 'USER'); "

            // 2. Tạo món đồ (Bảng items bây giờ rất gọn)
            + "INSERT INTO items (id, title, description, category, seller_id) VALUES "
            + "('ITM001', 'Laptop Dell XPS 15', 'Máy cũ 99%, chip i7', 'Điện tử', 'U001'), "
            + "('ITM002', 'Đồng hồ Rolex', 'Hàng chính hãng, full box', 'Trang sức', 'U001'); "

            // 3. Tạo ảnh cho món đồ (Bảng item_images - Một món có nhiều ảnh)
            + "INSERT INTO item_images (id, image_path, item_id) VALUES "
            + "('IMG001', 'dell_front.png', 'ITM001'), " + "('IMG002', 'dell_side.png', 'ITM001'), "
            + "('IMG003', 'rolex_main.png', 'ITM002'); "

            // 4. Đưa món đồ lên sàn đấu giá (Bảng auctions)
            + "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id) VALUES "
            + "('AUC001', 15000000, 15000000, '2026-05-10 08:00:00', '2026-05-15 20:00:00', 'OPEN', 'ITM001'), "
            + "('AUC002', 80000000, 80000000, '2026-05-11 09:00:00', '2026-05-16 21:00:00', 'OPEN', 'ITM002');";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        Statement stmt = conn.createStatement()) {

      // Xóa dữ liệu cũ trước khi nạp mới để tránh lỗi trùng ID (Duplicate PK)
      stmt.execute("DELETE FROM auto_bids; DELETE FROM bids; DELETE FROM auctions; "
          + "DELETE FROM item_images; DELETE FROM items; DELETE FROM users;");

      // Thực thi nạp dữ liệu
      stmt.executeUpdate(sql);

      System.out.println("--------------------------------------------------");
      System.out.println("✅ THÀNH CÔNG: Đã nạp dữ liệu mẫu vào cấu trúc mới!");
      System.out.println("   - Đã tạo 2 người dùng mẫu.");
      System.out.println("   - Đã tạo 2 sản phẩm.");
      System.out.println("   - Đã tách 3 ảnh vào bảng item_images.");
      System.out.println("   - Đã đưa 2 sản phẩm lên sàn đấu giá (auctions).");
      System.out.println("--------------------------------------------------");

    } catch (Exception e) {
      System.err.println("❌ Lỗi khi nạp dữ liệu test: " + e.getMessage());
      e.printStackTrace();
    }
  }
}

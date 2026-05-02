package com.nhomX.example;

import java.sql.Connection;
import java.sql.Statement;
import com.nhomX.example.utils.DatabaseConnection;

public class DatabaseTest {
  public static void main(String[] args) {
    // Đã xóa bỏ cột category và dữ liệu tương ứng để khớp với DB của bạn
    String sql =
        "INSERT INTO items (id, title, description, starting_price, current_price, image_path) VALUES "
            + "('ITM001', 'Laptop Dell XPS 15', 'Laptop mỏng nhẹ', 1500.0, 1500.0, '/com/nhomX/example/images/item1.png'), "
            + "('ITM002', 'Đồng hồ Rolex', 'Tình trạng 99%', 8500.0, 8500.0, '/com/nhomX/example/images/item2.png'), "
            + "('ITM003', 'Tranh Đêm Đầy Sao', 'Bản sao sơn dầu', 200.0, 200.0, '/com/nhomX/example/images/item3.png'), "
            + "('ITM004', 'iPhone 15 Pro Max', 'Màu Titan tự nhiên', 1100.0, 1100.0, '/com/nhomX/example/images/item4.png'), "
            + "('ITM005', 'Dây chuyền vàng', 'Đính đá Sapphire', 450.0, 450.0, '/com/nhomX/example/images/item5.png'), "
            + "('ITM006', 'Tượng Quan Âm', 'Chạm khắc thủ công', 350.0, 350.0, '/com/nhomX/example/images/item6.png'), "
            + "('ITM007', 'Tai nghe Sony', 'Chống ồn đỉnh cao', 300.0, 300.0, '/com/nhomX/example/images/item7.png'), "
            + "('ITM008', 'Nhẫn kim cương', 'Giấy kiểm định GIA', 4200.0, 4200.0, '/com/nhomX/example/images/item8.png'), "
            + "('ITM009', 'MacBook Pro M3', 'Dành cho Creator', 3200.0, 3200.0, '/com/nhomX/example/images/item9.png'), "
            + "('ITM010', 'Bình sứ Minh Long', 'Bản giới hạn', 150.0, 150.0, '/com/nhomX/example/images/item10.png');";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.executeUpdate(sql);
      System.out.println("✅ THÀNH CÔNG: Đã nhét 10 sản phẩm vào Database!");

    } catch (Exception e) {
      System.err.println("❌ Lỗi rồi: " + e.getMessage());
    }
  }
}

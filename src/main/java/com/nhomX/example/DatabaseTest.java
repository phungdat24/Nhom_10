package com.nhomX.example;

import java.sql.Connection;
import java.sql.Statement;
import com.nhomX.example.utils.DatabaseConnection;

public class DatabaseTest {
  public static void main(String[] args) {
    // Đã xóa bỏ cột category và dữ liệu tương ứng để khớp với DB của bạn
    String sql = "INSERT INTO items (id, title, description, starting_price, current_price) VALUES "
        + "('ITM001', 'Laptop Dell XPS 15', 'Laptop văn phòng mỏng nhẹ, màn OLED, Core i7', 1500.0, 1500.0), "
        + "('ITM002', 'Đồng hồ Rolex Submariner', 'Đồng hồ cơ Thụy Sỹ chính hãng, tình trạng 99%', 8500.0, 8500.0), "
        + "('ITM003', 'Bức tranh Đêm Đầy Sao', 'Bản sao sơn dầu chất lượng cao của Van Gogh', 200.0, 200.0), "
        + "('ITM004', 'iPhone 15 Pro Max', 'Màu Titan tự nhiên, bản 256GB mới nguyên seal', 1100.0, 1100.0), "
        + "('ITM005', 'Dây chuyền vàng 18K', 'Mặt đính đá Sapphire xanh dương cao cấp', 450.0, 450.0), "
        + "('ITM006', 'Tượng gỗ Quan Âm', 'Chạm khắc thủ công tinh xảo từ nguyên khối gỗ hương', 350.0, 350.0), "
        + "('ITM007', 'Tai nghe Sony WH-1000XM5', 'Tai nghe chụp tai chống ồn chủ động đỉnh cao', 300.0, 300.0), "
        + "('ITM008', 'Nhẫn kim cương 1 Carat', 'Giấy kiểm định GIA đầy đủ, nước D, độ tinh khiết VVS1', 4200.0, 4200.0), "
        + "('ITM009', 'MacBook Pro M3 Max', 'Cỗ máy trạm di động dành cho Creator chuyên nghiệp', 3200.0, 3200.0), "
        + "('ITM010', 'Bình gốm sứ Minh Long', 'Hàng thủ công mỹ nghệ phiên bản giới hạn', 150.0, 150.0);";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.executeUpdate(sql);
      System.out.println("✅ THÀNH CÔNG: Đã nhét 10 sản phẩm vào Database!");

    } catch (Exception e) {
      System.err.println("❌ Lỗi rồi: " + e.getMessage());
    }
  }
}

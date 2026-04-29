package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.nhomX.example.model.GeneralItem; // Dùng để khởi tạo khi đọc từ DB
import com.nhomX.example.model.Items; // Sử dụng class Items của bạn
import com.nhomX.example.utils.DatabaseConnection;

public class ItemRepositoryImpl implements ItemRepository {

  // Lấy kết nối duy nhất từ lớp Singleton vừa tạo
  private final Connection conn = DatabaseConnection.getInstance().getConnection();

  @Override
  public void save(Items item) {
    // Dùng dấu ? để tránh lỗi SQL Injection (bảo mật)
    String sql =
        "INSERT INTO items (id, title, description, starting_price, current_price, end_time, seller_id) VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, item.getId());
      pstmt.setString(2, item.getTitle());
      pstmt.setString(3, item.getDescription());
      pstmt.setDouble(4, item.getStartingPrice());
      pstmt.setDouble(5, item.getCurrentPrice());
      // Ép kiểu LocalDateTime thành String để lưu vào SQLite
      pstmt.setString(6, item.getEndTime() != null ? item.getEndTime().toString() : null);
      pstmt.setString(7, item.getSellerId());

      pstmt.executeUpdate();
      System.out.println("✅ Đã lưu Item vào database: " + item.getTitle());
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu Item: " + e.getMessage());
    }
  }

  @Override
  public Items findById(String id) {
    String sql = "SELECT * FROM items WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, id);
      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        // Khởi tạo đối tượng GeneralItem từ dữ liệu DB
        GeneralItem item =
            new GeneralItem(rs.getString("id"), rs.getString("title"), rs.getString("seller_id"));
        item.setDescription(rs.getString("description"));
        item.setStartingPrice(rs.getDouble("starting_price"));
        item.setCurrentPrice(rs.getDouble("current_price"));

        String endTimeStr = rs.getString("end_time");
        if (endTimeStr != null) {
          item.setEndTime(LocalDateTime.parse(endTimeStr));
        }

        return item;
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi tìm Item: " + e.getMessage());
    }
    return null; // Trả về null nếu không tìm thấy
  }

  @Override
  public void update(Items item) {
    // Tương tự hàm save, dùng UPDATE SET thay vì INSERT INTO
    // (Sẽ triển khai chi tiết sau)
  }

  @Override
  public List<Items> findAll() {
    return new ArrayList<>(); // Tạm thời trả về list rỗng, bổ sung sau
  }

  @Override
  public List<Items> findByCategory(String category) {
    return new ArrayList<>(); // Tạm thời trả về list rỗng, bổ sung sau
  }
}

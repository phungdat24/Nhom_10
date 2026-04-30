package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.nhomX.example.model.User;

public class UserRepositoryImpl implements UserRepository {

  // Lấy kết nối duy nhất từ DatabaseConnection
  private final Connection conn = DatabaseConnection.getInstance().getConnection();

  @Override
  public boolean register(User user) { // Đã sửa thành boolean theo đúng Interface
    String sql =
        "INSERT INTO users (id, username, password, fullname, balance) VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Đã đổi lại tên hàm cho khớp 100% với file User.java của nhóm bạn
      pstmt.setString(1, user.getUserId());
      pstmt.setString(2, user.getUserName());
      pstmt.setString(3, user.getPassword());
      pstmt.setString(4, user.getFullName());
      pstmt.setDouble(5, user.getBalance());

      int rowsAffected = pstmt.executeUpdate();
      if (rowsAffected > 0) {
        System.out.println("✅ Đã tạo tài khoản thành công cho: " + user.getUserName());
        return true; // Trả về true nếu thêm vào DB thành công
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi đăng ký user: " + e.getMessage());
    }
    return false; // Trả về false nếu có lỗi
  }

  @Override
  public User login(String username, String password) {
    String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, username);
      pstmt.setString(2, password);
      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        return new User(rs.getString("id"), rs.getString("username"), rs.getString("password"),
            rs.getString("fullname"), rs.getDouble("balance"));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi đăng nhập: " + e.getMessage());
    }
    return null;
  }

  @Override
  public User findById(String id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, id);
      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        return new User(rs.getString("id"), rs.getString("username"), rs.getString("password"),
            rs.getString("fullname"), rs.getDouble("balance"));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi tìm kiếm user: " + e.getMessage());
    }
    return null;
  }

  @Override
  public void updateBalance(String userId, double newBalance) {
    String sql = "UPDATE users SET balance = ? WHERE id = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setDouble(1, newBalance);
      pstmt.setString(2, userId);
      pstmt.executeUpdate();
      System.out.println("✅ Đã cập nhật số dư thành công!");
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật số dư: " + e.getMessage());
    }
  }
}

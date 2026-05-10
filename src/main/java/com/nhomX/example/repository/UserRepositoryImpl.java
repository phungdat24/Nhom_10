package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.nhomX.example.model.Admin;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.utils.DatabaseConnection;

public class UserRepositoryImpl implements UserRepository {

  @Override
  public boolean register(User user) {
    String sql =
        "INSERT INTO users (id, username, password, fullname, balance, role) VALUES (?,? , ?, ?, ?, ?)";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, user.getId());
      pstmt.setString(2, user.getUserName());
      pstmt.setString(3, user.getPasswordHash());
      pstmt.setString(4, user.getFullName());
      pstmt.setLong(5, user.getBalance());
      // Lưu Role xuống DB:
      pstmt.setString(6, user.getRoleName());

      int rowsAffected = pstmt.executeUpdate();
      if (rowsAffected > 0) {
        System.out.println("✅ Đã tạo tài khoản thành công cho: " + user.getUserName());
        return true;
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi đăng ký user: " + e.getMessage());
    }
    return false;
  }

  @Override
  public User login(String username, String password) {
    String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, username);
      pstmt.setString(2, password);
      try(ResultSet rs = pstmt.executeQuery()) {

        if (rs.next()) {
          return mapRowToUser(rs);
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi đăng nhập: " + e.getMessage());
    }
    return null;
  }

  @Override
  public User findById(String id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, id);

      try (ResultSet rs = pstmt.executeQuery()) {

        if (rs.next()) {
          return mapRowToUser(rs);
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi tìm kiếm user: " + e.getMessage());
    }
    return null;
  }

  @Override
  public void updateBalance(String userId, long deltaAmount) {
    String sql = "UPDATE users SET balance = balance + ? WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // VD: Truyền +50000 để nạp, -50000 để trừ:
      pstmt.setLong(1, deltaAmount);
      pstmt.setString(2, userId);
      pstmt.executeUpdate();
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật số dư: " + e.getMessage());
    }
  }
  // Đọc User từ ResultSet:
  private User mapRowToUser(ResultSet rs) throws SQLException {
    String roleString = rs.getString("role");

    // 1. Phân nhánh Admin
    if (roleString != null && roleString.contains("ADMIN")) {
      return new Admin(
              rs.getString("id"), rs.getString("username"),
              rs.getString("password"), rs.getString("fullname"),
              rs.getLong("balance")
      );
    }

    // 2. Phân nhánh Người dùng thường
    RegularUser user = new RegularUser(
            rs.getString("id"), rs.getString("username"),
            rs.getString("password"), rs.getString("fullname"),
            rs.getLong("balance")
    );

    // 3. Cấp lại quyền
    if (roleString != null) {
      if (roleString.contains("BIDDER")) {
        user.addRole(Role.BIDDER);
      }
      if (roleString.contains("SELLER")) {
        user.addRole(Role.SELLER);
      }
    }
    return user;
  }
}

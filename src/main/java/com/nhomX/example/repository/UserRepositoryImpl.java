package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.nhomX.example.model.Admin;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.utils.DatabaseConnection;

public class UserRepositoryImpl implements UserRepository {

  @Override
  public boolean register(User user) {
    // Kiểm tra username đã tồn tại trước khi INSERT
    if (findByUsername(user.getUserName()) != null) {
      System.err.println("❌ Email đã tồn tại: " + user.getUserName());
      return false;
    }
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
  public User login(String username, String passwordHash) {
    String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, username);
      pstmt.setString(2, passwordHash);
      try (ResultSet rs = pstmt.executeQuery()) {

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
    String sql = "UPDATE users SET balance = balance + ? WHERE id = ? AND (balance + ?) >=0";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // VD: Truyền +50000 để nạp, -50000 để trừ:
      pstmt.setLong(1, deltaAmount);
      pstmt.setString(2, userId);
      pstmt.setLong(3, deltaAmount);
      int rows = pstmt.executeUpdate();
      if (rows == 0) {
        System.err.println("❌ Cập nhật số dư thất bại: số dư không đủ hoặc user không tồn tại.");
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật số dư: " + e.getMessage());
    }
  }

  @Override
  public User findByUsername(String username) {
    String sql = "SELECT * FROM users WHERE username = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, username);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return mapRowToUser(rs);
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi tìm user theo username: " + e.getMessage());
    }
    return null;
  }

  @Override
  public List<User> findAll() {
    List<User> users = new ArrayList<>();
    String sql = "SELECT * FROM users";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {
      while (rs.next()) {
        users.add(mapRowToUser(rs));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi lấy danh sách user: " + e.getMessage());
    }
    return users;
  }
  @Override
  public boolean updatePassword(String username, String newPasswordHash) {
    String sql = "UPDATE users SET password = ? WHERE username = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, newPasswordHash);
      pstmt.setString(2, username);

      int rowsAffected = pstmt.executeUpdate();
      return rowsAffected > 0; // Trả về true nếu cập nhật thành công ít nhất 1 dòng

    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi cập nhật mật khẩu: " + e.getMessage());
    }
    return false;
  }

  // Đọc User từ ResultSet:
  private User mapRowToUser(ResultSet rs) throws SQLException {
    String roleString = rs.getString("role");

    // 1. Phân nhánh Admin
    if (roleString != null && roleString.contains("ADMIN")) {
      return new Admin(rs.getString("id"), rs.getString("username"), rs.getString("password"),
          rs.getString("fullname"), rs.getLong("balance"));
    }

    // 2. Phân nhánh Người dùng thường
    RegularUser user = new RegularUser(rs.getString("id"), rs.getString("username"),
        rs.getString("password"), rs.getString("fullname"), rs.getLong("balance"));

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

  @Override
  public int getTotalUserCount() {
    int count = 0;
    String sql = "SELECT COUNT(*) FROM users";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      if (rs.next()) {
        count = rs.getInt(1);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi getTotalUserCount: " + e.getMessage());
    }
    return count;
  }
}

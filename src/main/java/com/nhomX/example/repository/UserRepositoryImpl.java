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
    /*
    *Cột username được khai báo UNIQUE chính là chìa khóa.
    *Nghĩa là SQLite: "Tuyệt đối không bao giờ được cho phép 2 dòng có cùng giá trị ở cột này".
    *Khi code Java cố tình chạy lệnh INSERT với một email đã tồn tại, Database sẽ lập tức từ chối bản ghi đó và ném ngược lỗi SQLException về cho Java.
    */
    String sql =
        "INSERT INTO users (id, username, password, fullname, balance, role) VALUES (?,? , ?, ?, ?, ?)";

    // 1. GOM CONNECTION VÀO TRY ĐỂ TRÁNH LEAK
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      ensureUserActiveColumn(conn);
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
      }
    } catch (SQLException e) {
      // 3. ĐỂ DATABASE TỰ CHẶN EMAIL TRÙNG LẶP DỰA TRÊN UNIQUE CONSTRAINT
      if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
        System.err.println("❌ Email đã tồn tại: " + user.getUserName());
      } else {
        System.err.println("❌ Lỗi đăng ký user: " + e.getMessage());
      }
    }
    return false;
  }

  @Override
  public User login(String username, String passwordHash) {
    String sql = "SELECT * FROM users WHERE username = ? AND password = ? AND is_active = 1";
    // 1. MỞ KẾT NỐI VÀ TỰ ĐỘNG ĐÓNG
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

      // Gọi hàm phụ trợ thoải mái. Nếu hàm này quăng lỗi SQLException,
      // nó sẽ bay thẳng xuống catch ở cuối, và conn VẪN ĐƯỢC ĐÓNG AN TOÀN!
      ensureUserActiveColumn(conn);

      try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, username);
        pstmt.setString(2, passwordHash);
        try (ResultSet rs = pstmt.executeQuery()) {

          if (rs.next()) {
            return mapRowToUser(rs);
          }
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

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
    // Mở Connection 1 lần duy nhất, bao trùm toàn bộ
    try(Connection conn = DatabaseConnection.getInstance().getConnection()){
      // 1. Kiểm tra cột (Dùng try-catch thường bên trong)
      try {
        ensureUserActiveColumn(conn);
      } catch (SQLException e) {
        System.err.println("❌ Lỗi kiểm tra cột is_active: " + e.getMessage());
        return users; // Lỗi thì dừng luôn, trả về list rỗng
      }

      // 2. Chạy Query lấy dữ liệu (Sử dụng chung biến conn đang mở)
      try (PreparedStatement pstmt = conn.prepareStatement(sql);
           ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          users.add(mapRowToUser(rs));
        }
      } catch (SQLException e) {
        System.err.println("❌ Lỗi lấy danh sách user: " + e.getMessage());
      }

    } catch (SQLException e) {
      // Bắt lỗi tổng (ví dụ lỗi không mở được file database)
      System.err.println("❌ Lỗi khởi tạo kết nối DB: " + e.getMessage());
    }

    return users;
  }
  @Override
  public boolean updatePassword(String username, String newPasswordHash) {
    String sql = "UPDATE users SET password = ? WHERE username = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      if (rs.next()) {
        count = rs.getInt(1);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi getTotalUserCount: " + e.getMessage());
    }
    return count;
  }

  /**
   * ALTER TABLE cần chạy 1 lần trên DB để thêm cột is_active:
   *
   * <p>ALTER TABLE users ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1;
   *
   * <p>SQLite dùng INTEGER (0/1), MySQL dùng TINYINT(1) hoặc BOOLEAN. Giá trị mặc định 1 = tất cả
   * user cũ đều được coi là "đang hoạt động".
   */
  private void ensureUserActiveColumn(Connection conn) throws SQLException {
    try (PreparedStatement pstmt = conn.prepareStatement("PRAGMA table_info(users)");
         ResultSet rs = pstmt.executeQuery()) {
      while (rs.next()) {
        if ("is_active".equalsIgnoreCase(rs.getString("name"))) {
          return;
        }
      }
    }

    try (PreparedStatement pstmt = conn.prepareStatement(
            "ALTER TABLE users ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1")) {
      pstmt.executeUpdate();
    }
  }


  @Override
  public boolean setUserActiveStatus(String userId, boolean isActive) {
    // UPDATE đúng 1 cột — không đụng mật khẩu, balance hay bất cứ thứ gì khác
    String sql = "UPDATE users SET is_active = ? WHERE id = ?";

    try(Connection conn = DatabaseConnection.getInstance().getConnection()) {
      try {
        ensureUserActiveColumn(conn);
      } catch (SQLException e) {
        System.err.println("❌ Lỗi kiểm tra cột is_active: " + e.getMessage());
        return false; // Lỗi thì dừng luôn, trả về list rỗng
      }
      try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setInt(1, isActive ? 1 : 0); // SQLite: 1=active, 0=locked
        pstmt.setString(2, userId);
        int rows = pstmt.executeUpdate();
        if (rows > 0) {
          System.out.println("✅ Đã " + (isActive ? "mở khóa" : "khóa") + " tài khoản: " + userId);
          return true;
        }
        System.err.println("❌ Không tìm thấy user để cập nhật trạng thái: " + userId);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi setUserActiveStatus: " + e.getMessage());
    }
    return false;
  }

  @Override
  public boolean deleteUser(String userId) {
    // XÓA THEO ĐÚNG THỨ TỰ FOREIGN KEY để tránh lỗi constraint:
    // 1. Xóa auto_bids của user này và của các phiên do user này đăng bán
    // 2. Xóa bids của user này và của các phiên do user này đăng bán
    // 3. Xóa item_images của user này
    // 4. Xóa auctions liên quan
    // 5. Xóa items của user này
    // 6. Cuối cùng mới xóa user
    try(Connection conn = DatabaseConnection.getInstance().getConnection()){
      conn.setAutoCommit(false);
      try {
        // Gỡ tham chiếu tới user từ những phiên vẫn được giữ lại.
        executeUpdate(conn, "UPDATE auctions SET winner_id = NULL WHERE winner_id = ?", userId);
        executeUpdate(conn, "UPDATE auctions SET approved_by = NULL WHERE approved_by = ?", userId);

        // Bước 1: Xóa cấu hình auto-bid
        executeUpdate(conn,
            "DELETE FROM auto_bids WHERE auction_id IN "
                + "(SELECT a.id FROM auctions a JOIN items i ON a.item_id = i.id "
                + "WHERE i.seller_id = ?)",
            userId);
        executeUpdate(conn, "DELETE FROM auto_bids WHERE user_id = ?", userId);

        // Bước 2: Xóa lịch sử đấu giá (bids)
        executeUpdate(conn,
            "DELETE FROM bids WHERE auction_id IN "
                + "(SELECT a.id FROM auctions a JOIN items i ON a.item_id = i.id "
                + "WHERE i.seller_id = ?)",
            userId);
        executeUpdate(conn, "DELETE FROM bids WHERE user_id = ?", userId);

        // Bước 3: Lấy danh sách item_id của user để xóa ảnh
        // (cần xóa item_images trước khi xóa items do FK)
        executeUpdate(conn,
            "DELETE FROM item_images WHERE item_id IN "
                + "(SELECT id FROM items WHERE seller_id = ?)",
            userId);

        // Bước 4: Xóa auctions của các items thuộc user
        // (cần trước khi xóa items)
        executeUpdate(conn,
            "DELETE FROM auctions WHERE item_id IN "
                + "(SELECT id FROM items WHERE seller_id = ?)",
            userId);

        // Bước 5: Xóa items
        executeUpdate(conn, "DELETE FROM items WHERE seller_id = ?", userId);

        // Bước 6: Cuối cùng xóa chính user
        int rows = executeUpdate(conn, "DELETE FROM users WHERE id = ?", userId);

        conn.commit();
        if (rows > 0) {
          System.out.println("✅ Đã xóa toàn bộ dữ liệu của user: " + userId);
          return true;
        }
        System.err.println("❌ Không tìm thấy user để xóa: " + userId);
        return false;

      } catch (SQLException e) {
        System.err.println("❌ Lỗi deleteUser — đang rollback: " + e.getMessage());
        rollbackSilently(conn);
        return false;
      } finally {
        restoreAutoCommit(conn);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi mở kết nối DB khi xóa User: " + e.getMessage());
      return false;
    }
  }

  @Override
  public boolean isUserActive(String userId) {
    String sql = "SELECT is_active FROM users WHERE id = ?";

    try(Connection conn = DatabaseConnection.getInstance().getConnection()) {
      try {
        ensureUserActiveColumn(conn);
      } catch (SQLException e) {
        System.err.println("❌ Lỗi kiểm tra cột is_active: " + e.getMessage());
        return true;
      }
      try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, userId);
        try (ResultSet rs = pstmt.executeQuery()) {
          if (rs.next()) {
            return rs.getInt("is_active") == 1;
          }
        }
      }
    }catch (SQLException e) {
      System.err.println("❌ Lỗi isUserActive: " + e.getMessage());
    }
    // Mặc định coi là active nếu không tìm thấy (tránh khóa nhầm)
    return true;
  }

  // ── Helper dùng chung trong deleteUser ───────────────────────────────────
  private int executeUpdate(Connection conn, String sql, String param) throws SQLException {
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, param);
      return pstmt.executeUpdate();
    }
  }

  private void rollbackSilently(Connection conn) {
    try {
      if (conn != null) {
        conn.rollback();
      }
    } catch (SQLException ex) {
      System.err.println("❌ Lỗi rollback: " + ex.getMessage());
    }
  }

  private void restoreAutoCommit(Connection conn) {
    try {
      if (conn != null) {
        conn.setAutoCommit(true);
      }
    } catch (SQLException ex) {
      System.err.println("❌ Lỗi restoreAutoCommit: " + ex.getMessage());
    }
  }
}

package com.nhomX.example.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

  public static final String URL_PROPERTY = "auction.db.url";
  private static final String DEFAULT_URL = "jdbc:sqlite:auction.db";
  // 1. Biến static lưu trữ instance duy nhất (Singleton)
  private static DatabaseConnection instance;

  // Đường dẫn tới file database SQLite
  private static String getConfiguredUrl() {
    return System.getProperty(URL_PROPERTY, DEFAULT_URL);
  }

  // 2. Constructor private để ngăn bên ngoài dùng từ khóa 'new'
  private DatabaseConnection() {
    // Chỉ khởi tạo cấu trúc bảng một lần
      createTables();
  }

  // 3. Phương thức public static để cung cấp instance duy nhất
  public static synchronized DatabaseConnection getInstance() {
    if (instance == null) {
      instance = new DatabaseConnection();
    }
    return instance;
  }
  // Factory Method - Trả về một kết nối MỚI mỗi lần gọi
  // Giúp tầng Repository thoải mái dùng try-with-resources để close()
  public Connection getConnection() throws SQLException {
    Connection conn = DriverManager.getConnection(getConfiguredUrl());

    // BẮT BUỘC BẬT CHẾ ĐỘ KIỂM TRA KHÓA NGOẠI CỦA SQLITE
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("PRAGMA foreign_keys = ON;");
    }
    return conn;
  }

  public static synchronized void resetForTests() {
    // Vì không còn giữ connection tĩnh, chỉ cần hủy instance
    instance = null;
    System.out.println("🔄 Đã reset Database Connection instance.");
  }

  // 4. Hàm khởi tạo cấu trúc các bảng theo sơ đồ ERD mới nhất
  private void createTables() {
    // Lấy 1 kết nối tạm thời chỉ để tạo bảng
    try (Connection conn = getConnection();
         Statement stmt = conn.createStatement()) {
    // Bảng Users: Quản lý người dùng và số dư
    String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" + "id TEXT PRIMARY KEY, "
        + "username TEXT UNIQUE NOT NULL, " + "password TEXT NOT NULL, " + "fullname TEXT, "
        + "balance INTEGER DEFAULT 0, " + "role TEXT DEFAULT 'USER');";

    // Bảng Items: Thông tin cơ bản của món đồ (Đã bỏ các cột giá và thời gian)
    String createItemsTable = "CREATE TABLE IF NOT EXISTS items (" + "id TEXT PRIMARY KEY, "
        + "title TEXT NOT NULL, " + "description TEXT, " + "category TEXT, " + "seller_id TEXT, "
        + "FOREIGN KEY(seller_id) REFERENCES users(id));";

    // Bảng Item_Images (Bảng mới): Hỗ trợ một món đồ có nhiều ảnh
    String createItemImagesTable = "CREATE TABLE IF NOT EXISTS item_images ("
        + "id TEXT PRIMARY KEY, " + "image_path TEXT NOT NULL, " + "item_id TEXT, "
        + "FOREIGN KEY(item_id) REFERENCES items(id));";

    // Bảng Auctions (Bảng mới): Trung tâm điều phối phiên đấu giá
    String createAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions (" + "id TEXT PRIMARY KEY, "
        + "starting_price INTEGER, " + "highest_bid INTEGER, " + "start_time DATETIME, "
        + "end_time DATETIME, " + "status TEXT DEFAULT 'PENDING', " + "item_id TEXT, "
        + "winner_id TEXT, " + "approved_by TEXT, " + "FOREIGN KEY(item_id) REFERENCES items(id), "
        + "FOREIGN KEY(winner_id) REFERENCES users(id), "
        + "FOREIGN KEY(approved_by) REFERENCES users(id));";

    // Bảng Bids: Lịch sử đặt giá (Đã đổi item_id thành auction_id)
    String createBidsTable = "CREATE TABLE IF NOT EXISTS bids (" + "id TEXT PRIMARY KEY, "
        + "amount INTEGER, " + "bid_time TEXT, " + "user_id TEXT, " + "auction_id TEXT, "
        + "FOREIGN KEY(user_id) REFERENCES users(id), "
        + "FOREIGN KEY(auction_id) REFERENCES auctions(id));";

    // Bảng Auto_Bids (Bảng mới): Hỗ trợ tính năng đấu giá tự động
    String createAutoBidsTable = "CREATE TABLE IF NOT EXISTS auto_bids (" + "id TEXT PRIMARY KEY, "
        + "max_price INTEGER, " + "step_price INTEGER, " + "is_active INTEGER DEFAULT 1, "+ "created_at TEXT, " + "user_id TEXT, "
        + "auction_id TEXT, " + "FOREIGN KEY(user_id) REFERENCES users(id), "
        + "FOREIGN KEY(auction_id) REFERENCES auctions(id), " + "UNIQUE(user_id, auction_id));";

      stmt.execute(createUsersTable);
      stmt.execute(createItemsTable);
      stmt.execute(createItemImagesTable);
      stmt.execute(createAuctionsTable);
      stmt.execute(createBidsTable);
      stmt.execute(createAutoBidsTable);
      System.out.println("✅ Khởi tạo cấu trúc Database mới thành công!");
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi tạo bảng: " + e.getMessage());
    }
  }
}

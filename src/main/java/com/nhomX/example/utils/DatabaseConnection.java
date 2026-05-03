package com.nhomX.example.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
  // 1. Biến static lưu trữ instance duy nhất (Đảm bảo chỉ có 1 kết nối)
  private static DatabaseConnection instance;
  private Connection connection;

  // Đường dẫn tới file database SQLite (Nằm ở thư mục gốc của project)
  private static final String URL = "jdbc:sqlite:auction.db";

  // 2. Constructor private để ngăn bên ngoài dùng từ khóa 'new'
  private DatabaseConnection() {
    try {
      // Khởi tạo kết nối qua JDBC
      connection = DriverManager.getConnection(URL);
      System.out.println("✅ Kết nối cơ sở dữ liệu SQLite thành công!");
      // Tự động tạo bảng nếu chưa tồn tại
      createTables();
    } catch (SQLException e) {
      System.err.println("❌ Lỗi kết nối database: " + e.getMessage());
    }
  }

  // 3. Phương thức public static để cung cấp instance duy nhất ra ngoài
  public static DatabaseConnection getInstance() {
    if (instance == null) {
      instance = new DatabaseConnection();
    }
    return instance;
  }

  // Phương thức để các Repository khác (User, Item) gọi và lấy kết nối
  public Connection getConnection() {
    return connection;
  }

  // 4. Hàm khởi tạo cấu trúc các bảng (Schema)
  private void createTables() {
    String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" + "id TEXT PRIMARY KEY, "
        + "username TEXT UNIQUE NOT NULL, " + "password TEXT NOT NULL, " + "fullname TEXT, "
        + "balance REAL" + ");";

    String createItemsTable = "CREATE TABLE IF NOT EXISTS items (" + "id TEXT PRIMARY KEY, "
        + "title TEXT NOT NULL, " + "description TEXT, " + "starting_price REAL, "
        + "current_price REAL, " + "end_time TEXT, " + "seller_id TEXT, " + "image_path TEXT, "
        + "status TEXT DEFAULT 'OPEN', " + "winner_id TEXT, "
        + "FOREIGN KEY(seller_id) REFERENCES users(id)" + ");";

    String createBidsTable = "CREATE TABLE IF NOT EXISTS bids (" + "id TEXT PRIMARY KEY, "
        + "bid_time TEXT, " + "user_id TEXT, " + "item_id TEXT, " + "amount REAL, "
        + "FOREIGN KEY(user_id) REFERENCES users(id), "
        + "FOREIGN KEY(item_id) REFERENCES items(id)" + ");";

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(createUsersTable);
      stmt.execute(createItemsTable);
      stmt.execute(createBidsTable);
      System.out.println("✅ Khởi tạo cấu trúc các bảng thành công.");
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi tạo bảng: " + e.getMessage());
    }
  }
}

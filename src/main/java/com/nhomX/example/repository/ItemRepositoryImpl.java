package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.factory.ItemFactory;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.User;
import com.nhomX.example.utils.DatabaseConnection;

public class ItemRepositoryImpl implements ItemRepository {
  private static final Logger logger = LoggerFactory.getLogger(ItemRepositoryImpl.class);


  // Lấy toàn bộ danh sách sản phẩm (SELECT *)
  @Override
  public List<Items> findAll() {
    List<Items> itemsList = new ArrayList<>();
    // Dùng JOIN để lấy luôn thông tin User chỉ trong 1 lần query
    String sql = "SELECT i.*, u.fullname AS seller_name, u.username AS seller_email "
        + "FROM items i LEFT JOIN users u ON i.seller_id = u.id";

    // Xử lý đóng kết nối an toàn (try-with-resources)
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      while (rs.next()) {
        // Đẩy dữ liệu vào danh sách
        itemsList.add(mapRowToItem(rs, conn));
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lấy danh sách sản phẩm", e);
    }
    return itemsList;
  }

  // Lọc sản phẩm theo danh mục (WHERE category = ?)
  @Override
  public List<Items> findByCategory(String category) {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT i.*, u.fullname AS seller_name, u.username AS seller_email "
        + "FROM items i LEFT JOIN users u ON i.seller_id = u.id " + "WHERE i.category = ?";

    // ✅ Nhiệm vụ 4: try-with-resources
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền tham số category vào dấu ?
      pstmt.setString(1, category);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          // Đẩy dữ liệu vào danh sách
          itemsList.add(mapRowToItem(rs, conn));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lọc sản phẩm theo danh mục", e);
    }
    return itemsList;
  }

  @Override
  public List<Items> findBySellerId(String sellerId) {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT i.*, u.fullname AS seller_name, u.username AS seller_email "
        + "FROM items i LEFT JOIN users u ON i.seller_id = u.id " + "WHERE i.seller_id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, sellerId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          itemsList.add(mapRowToItem(rs, conn));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lọc sản phẩm theo seller", e);
    }
    return itemsList;
  }

  // Hàm phụ: Lấy danh sách ảnh của 1 Item cụ thể
  private List<ItemImage> getImagesByItemId(String itemId, Connection conn) throws SQLException {
    List<ItemImage> imageList = new ArrayList<>();
    String sql = "SELECT * FROM item_images WHERE item_id = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, itemId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          imageList.add(new ItemImage(rs.getString("id"), rs.getString("image_path"), itemId));
        }
      }
    }
    return imageList;
  }

  @Override
  public Items findById(String id) {
    // 1. Triển khai logic truy vấn: Câu lệnh SQL tìm 1 bản ghi theo ID
    String sql = "SELECT i.*, u.fullname AS seller_name, u.username AS seller_email "
        + "FROM items i LEFT JOIN users u ON i.seller_id = u.id " + "WHERE i.id = ?";

    // 2 & 3. Xử lý đóng tài nguyên bằng try-with-resources cho PreparedStatement
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền ID người dùng muốn tìm vào dấu ?
      pstmt.setString(1, id);

      // Dùng thêm try-with-resources cho ResultSet để tự động đóng sau khi đọc xong
      try (ResultSet rs = pstmt.executeQuery()) {
        // Nếu rs.next() là true nghĩa là tìm thấy dữ liệu trong Database
        if (rs.next()) {
          // 4. Tái sử dụng code: Dùng hàm mapRowToItem có sẵn để convert dữ liệu
          return mapRowToItem(rs, conn);
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi tìm sản phẩm theo ID", e);
    }

    // 5. Giá trị trả về: Trả về null nếu không tìm thấy ID hoặc xảy ra lỗi
    return null;
  }

  @Override
  public void update(Items item) {
    // Câu lệnh SQL cập nhật dữ liệu, bao gồm cả cột image_path
    String sqlItem =
        "UPDATE items SET title = ?, description = ?, category = ?, seller_id = ? WHERE id = ?";
    String sqlDeleteImages = "DELETE FROM item_images WHERE item_id = ?";
    String sqlInsertImages = "INSERT INTO item_images (id, image_path, item_id) VALUES (?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      // ✅ FIX BUG 2: Bật Giao dịch
      conn.setAutoCommit(false);
      // 1. Cập nhật thông tin bảng items
      try {
        try (PreparedStatement pstmtItem = conn.prepareStatement(sqlItem)) {
          pstmtItem.setString(1, item.getTitle());
          pstmtItem.setString(2, item.getDescription());
          pstmtItem.setString(3, item.getCategory());
          pstmtItem.setString(4, item.getSeller().getId());
          pstmtItem.setString(5, item.getId());
          pstmtItem.executeUpdate();
        }

        // 2. Xóa sạch ảnh cũ của sản phẩm này
        try (PreparedStatement pstmtDel = conn.prepareStatement(sqlDeleteImages)) {
          pstmtDel.setString(1, item.getId());
          pstmtDel.executeUpdate();
        }
        insertImages(item, conn, sqlInsertImages);
        // 4. Chốt giao dịch
        conn.commit();
        logger.info("Đã cập nhật thành công sản phẩm: {}", item.getTitle());

      } catch (SQLException e) {
        logger.error("Lỗi giao dịch cập nhật sản phẩm. Đang rollback", e);
        rollbackSilently(conn);
      } finally {
        restoreAutoCommit(conn);
      }
    } catch (SQLException e) {
      logger.error("Lỗi mở kết nối DB khi cập nhật sản phẩm", e);
    }
  }

  @Override
  public void save(Items item) {
    String sqlItem =
        "INSERT INTO items (id, title, description, category, seller_id) VALUES (?, ?, ?, ?, ?)";
    String sqlImage = "INSERT INTO item_images (id, image_path, item_id) VALUES (?, ?, ?)";

    // ĐƯA CONNECTION VÀO TRY ĐỂ CHỐNG LEAK
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      conn.setAutoCommit(false);
      // ✅ FIX BUG 2: Bật chế độ Giao dịch
      try {
        // 1. Lưu Sản phẩm vào bảng items
        try (PreparedStatement pstmtItem = conn.prepareStatement(sqlItem)) {
          pstmtItem.setString(1, item.getId());
          pstmtItem.setString(2, item.getTitle());
          pstmtItem.setString(3, item.getDescription());
          pstmtItem.setString(4, item.getCategory());
          pstmtItem.setString(5, item.getSeller() != null ? item.getSeller().getId() : null);;
          pstmtItem.executeUpdate();
        }

        insertImages(item, conn, sqlImage);

        // Chốt giao dịch
        conn.commit();
        logger.info("Đã lưu thành công sản phẩm: {}", item.getTitle());

      } catch (SQLException e) {
        logger.error("Lỗi giao dịch lưu sản phẩm. Đang rollback", e);
        rollbackSilently(conn);
      } finally {
        restoreAutoCommit(conn);
      }
    } catch (SQLException e) {
      logger.error("Lỗi mở kết nối DB khi lưu sản phẩm", e);
    }
  }

  public void save(Items item, Connection conn) throws SQLException {
    String sqlItem =
        "INSERT INTO items (id, title, description, category, seller_id) VALUES (?, ?, ?, ?, ?)";
    String sqlImage = "INSERT INTO item_images (id, image_path, item_id) VALUES (?, ?, ?)";

    try (PreparedStatement pstmtItem = conn.prepareStatement(sqlItem)) {
      pstmtItem.setString(1, item.getId());
      pstmtItem.setString(2, item.getTitle());
      pstmtItem.setString(3, item.getDescription());
      pstmtItem.setString(4, item.getCategory());
      pstmtItem.setString(5, item.getSeller() != null ? item.getSeller().getId() : null);
      pstmtItem.executeUpdate();
    }

    insertImages(item, conn, sqlImage);
  }

  @Override
  public boolean deleteItemAndAuction(String itemId) {
    // [Database Transaction] Phải xóa từ bảng con (ảnh, lịch sử, auto_bid) lên bảng cha (auction, item)
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      conn.setAutoCommit(false); // Bắt đầu Transaction
      try {
        // Lấy auction_id tương ứng với item_id
        String auctionId = null;
        try (PreparedStatement getAucStmt = conn.prepareStatement("SELECT id FROM auctions WHERE item_id = ?")) {
          getAucStmt.setString(1, itemId);
          ResultSet rs = getAucStmt.executeQuery();
          if (rs.next()) {
            auctionId = rs.getString("id");
          }
        }

        if (auctionId != null) {
          // 1. Xóa Auto-bids của phiên này
          executeUpdate(conn, "DELETE FROM auto_bids WHERE auction_id = ?", auctionId);
          // 2. Xóa Bids (lịch sử đặt giá)
          executeUpdate(conn, "DELETE FROM bids WHERE auction_id = ?", auctionId);
          // 3. Xóa Phiên đấu giá
          executeUpdate(conn, "DELETE FROM auctions WHERE id = ?", auctionId);
        }

        // 4. Xóa Ảnh sản phẩm
        executeUpdate(conn, "DELETE FROM item_images WHERE item_id = ?", itemId);
        // 5. Xóa Sản phẩm gốc
        int rows = executeUpdate(conn, "DELETE FROM items WHERE id = ?", itemId);

        conn.commit(); // Hoàn tất Transaction
        return rows > 0;
      } catch (SQLException e) {
        conn.rollback(); // Có lỗi thì lùi lại toàn bộ
        logger.error("Lỗi xóa sản phẩm - đã rollback", e);
        return false;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      logger.error("Lỗi kết nối DB khi xóa sản phẩm", e);
      return false;
    }
  }

  // Hàm tiện ích chạy câu lệnh Delete
  private int executeUpdate(Connection conn, String sql, String param) throws SQLException {
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, param);
      return pstmt.executeUpdate();
    }
  }

  private void insertImages(Items item, Connection conn, String sql) throws SQLException {
    List<ItemImage> images = item.getImages();
    if (images == null || images.isEmpty())
      return;
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      for (ItemImage img : images) {
        pstmt.setString(1, img.getId());
        pstmt.setString(2, img.getImagePath());
        pstmt.setString(3, item.getId());
        pstmt.addBatch();
      }
      pstmt.executeBatch();
    }
  }

  private void rollbackSilently(Connection conn) {
    try {
      if (conn != null)
        conn.rollback();
    } catch (SQLException ex) {
      logger.error("Lỗi rollback", ex);
    }
  }

  private void restoreAutoCommit(Connection conn) {
    try {
      if (conn != null)
        conn.setAutoCommit(true);
    } catch (SQLException ex) {
      logger.error("Lỗi khôi phục autoCommit", ex);
    }
  }

  private Items mapRowToItem(ResultSet rs, Connection conn) throws SQLException {
    String category = rs.getString("category");
    String id = rs.getString("id");
    String title = rs.getString("title");
    String description = rs.getString("description");
    String sellerId = rs.getString("seller_id");

    // Khởi tạo User bằng dữ liệu lấy trực tiếp từ cột JOIN (seller_name, seller_email)
    User fullSeller = new RegularUser();
    if (sellerId != null) {
      fullSeller.setId(sellerId);
      fullSeller.setFullName(rs.getString("seller_name"));
      fullSeller.setUserName(rs.getString("seller_email"));
    }

    Items item = ItemFactory.createItem(category, id, title, description, fullSeller);
    item.setImages(getImagesByItemId(item.getId(), conn));

    return item;
  }
}



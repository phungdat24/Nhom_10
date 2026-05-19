package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.nhomX.example.factory.ItemFactory;
import com.nhomX.example.model.Art;
import com.nhomX.example.model.Electronics;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.Jewelry;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class ItemRepositoryImpl implements ItemRepository {

  // Lấy toàn bộ danh sách sản phẩm (SELECT *)
  @Override
  public List<Items> findAll() {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    // Xử lý đóng kết nối an toàn (try-with-resources)
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      while (rs.next()) {
        // Đẩy dữ liệu vào danh sách
        itemsList.add(mapRowToItem(rs, conn));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
    }
    return itemsList;
  }

  // Lọc sản phẩm theo danh mục (WHERE category = ?)
  @Override
  public List<Items> findByCategory(String category) {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items WHERE category = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    // ✅ Nhiệm vụ 4: try-with-resources
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền tham số category vào dấu ?
      pstmt.setString(1, category);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          // Đẩy dữ liệu vào danh sách
          itemsList.add(mapRowToItem(rs, conn));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lọc sản phẩm theo danh mục: " + e.getMessage());
    }
    return itemsList;
  }

  @Override
  public List<Items> findBySellerId(String sellerId) {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items WHERE seller_id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, sellerId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          itemsList.add(mapRowToItem(rs, conn));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lọc sản phẩm theo seller: " + e.getMessage());
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
          imageList.add(new ItemImage(rs.getString("id"), rs.getString("image_path")));
        }
      }
    }
    return imageList;
  }

  @Override
  public Items findById(String id) {
    // 1. Triển khai logic truy vấn: Câu lệnh SQL tìm 1 bản ghi theo ID
    String sql = "SELECT * FROM items WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    // 2 & 3. Xử lý đóng tài nguyên bằng try-with-resources cho PreparedStatement
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
      System.err.println("❌ Lỗi khi tìm sản phẩm theo ID: " + e.getMessage());
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
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try {
      // ✅ FIX BUG 2: Bật Giao dịch
      conn.setAutoCommit(false);
      // 1. Cập nhật thông tin bảng items
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
      System.out.println("✅ Đã cập nhật thành công sản phẩm: " + item.getTitle());

    } catch (SQLException e) {
      System.err.println("❌ Lỗi Giao dịch Cập Nhật Sản Phẩm! Đang Rollback... " + e.getMessage());
      try {
        if (conn != null) {
          conn.rollback();
        }
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi Rollback: " + ex.getMessage());
      }
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi khôi phục AutoCommit: " + ex.getMessage());
      }
    }
  }

  @Override
  public void save(Items item) {
    String sqlItem =
        "INSERT INTO items (id, title, description, category, seller_id) VALUES (?, ?, ?, ?, ?)";
    String sqlImage = "INSERT INTO item_images (id, image_path, item_id) VALUES (?, ?, ?)";

    Connection conn = DatabaseConnection.getInstance().getConnection();
    // ✅ FIX BUG 2: Bật chế độ Giao dịch
    try {
      conn.setAutoCommit(false);
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
      System.out.println("✅ Đã lưu thành công sản phẩm: " + item.getTitle());

    } catch (SQLException e) {
      System.err.println("❌ Lỗi Giao dịch Lưu Sản Phẩm! Đang Rollback... " + e.getMessage());
      try {
        if (conn != null)
          conn.rollback();
        System.out.println("🔄 Đã hoàn tác an toàn.");
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi nghiêm trọng khi Rollback: " + ex.getMessage());
      }
    } finally {
      try {
        if (conn != null)
          conn.setAutoCommit(true);
      } catch (SQLException e) {
        System.err.println("❌ Lỗi kết nối DB: " + e.getMessage());
      }
    }
  }

  @Override
  public void delete(String itemId) {
    // [FIX] Xóa ảnh trước, rồi mới xóa item (tránh lỗi foreign key constraint)
    String sqlDeleteImages = "DELETE FROM item_images WHERE item_id = ?";
    String sqlDeleteItem = "DELETE FROM items WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try {
      conn.setAutoCommit(false);
      try (PreparedStatement pstmt1 = conn.prepareStatement(sqlDeleteImages)) {
        pstmt1.setString(1, itemId);
        pstmt1.executeUpdate();
      }
      try (PreparedStatement pstmt2 = conn.prepareStatement(sqlDeleteItem)) {
        pstmt2.setString(1, itemId);
        pstmt2.executeUpdate();
      }
      conn.commit();
      System.out.println("✅ Đã xóa sản phẩm: " + itemId);
    } catch (SQLException e) {
      System.err.println("❌ Lỗi xóa sản phẩm! Đang rollback... " + e.getMessage());
      rollbackSilently(conn);
    } finally {
      restoreAutoCommit(conn);
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
      System.err.println("❌ Lỗi rollback: " + ex.getMessage());
    }
  }

  private void restoreAutoCommit(Connection conn) {
    try {
      if (conn != null)
        conn.setAutoCommit(true);
    } catch (SQLException ex) {
      System.err.println("❌ Lỗi khôi phục autoCommit: " + ex.getMessage());
    }
  }

  private Items mapRowToItem(ResultSet rs, Connection conn) throws SQLException {
    // 1. Trích xuất toàn bộ dữ liệu thô từ Database
    String category = rs.getString("category");
    String id = rs.getString("id");
    String title = rs.getString("title");
    String description = rs.getString("description");

    // 2. Tạo vỏ rỗng chứa ID của người bán
    RegularUser seller = new RegularUser();
    seller.setId(rs.getString("seller_id"));

    // 3. Giao toàn quyền sinh sát cho Nhà máy (Khử hoàn toàn if-else/switch-case)
    Items item = ItemFactory.createItem(category, id, title, description, seller);

    // 4. Nhét thêm danh sách ảnh và trả về
    item.setImages(getImagesByItemId(item.getId(), conn));

    return item;
  }
}



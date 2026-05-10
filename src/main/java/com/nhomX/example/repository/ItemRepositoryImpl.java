package com.nhomX.example.repository;

import com.nhomX.example.model.*;
import com.nhomX.example.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemRepositoryImpl implements ItemRepository {

  //Lấy toàn bộ danh sách sản phẩm (SELECT *)
  @Override
  public List<Items> findAll() {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    //Xử lý đóng kết nối an toàn (try-with-resources)
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

  //Lọc sản phẩm theo danh mục (WHERE category = ?)
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

  // Hàm phụ: Lấy danh sách ảnh của 1 Item cụ thể
  private List<ItemImage> getImagesByItemId(String itemId, Connection conn) throws SQLException {
    List<ItemImage> imageList = new ArrayList<>();
    String sql = "SELECT * FROM item_images WHERE item_id = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, itemId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          imageList.add(new ItemImage(
                  rs.getString("id"),
                  rs.getString("image_path")
          ));
        }
      }
    }
    return imageList;
  }

  // ✅ Nhiệm vụ 3: Hàm phụ dùng để ánh xạ (map) dữ liệu từ ResultSet vào đối tượng Items
  private Items mapRowToItem(ResultSet rs, Connection conn) throws SQLException {
    Items item;
    String category = rs.getString("category");
    // Khởi tạo đúng lớp con dựa trên cột category
    if (category != null) {
      switch (category.toUpperCase()) {
        case "ELECTRONICS":
          item = new Electronics();
          break;
        case "JEWELRY":
          item = new Jewelry();
          break;
        case "ART":
          item = new Art();
          break;
        default:
          item = new GeneralItem();
          break;
      }
    } else {
      item = new GeneralItem();
    }

    // Ánh xạ các thuộc tính:
    item.setId(rs.getString("id"));
    item.setTitle(rs.getString("title"));
    item.setDescription(rs.getString("description"));
    //Quét DB lấy danh sách ảnh nhét vào Object
    item.setImages(getImagesByItemId(item.getId(), conn));
    // Object cho seller
    RegularUser seller = new RegularUser();
    seller.setId(rs.getString("seller_id"));
    item.setStartingPrice(rs.getLong("starting_price"));
    item.setSeller(seller);

    return item;
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

      // 3. Chèn lại danh sách ảnh mới (nếu có)
      List<ItemImage> images = item.getImages();
      if (images != null && !images.isEmpty()) {
        try (PreparedStatement pstmtImg = conn.prepareStatement(sqlInsertImages)) {
          for (ItemImage img : images) {
            pstmtImg.setString(1, img.getId());
            pstmtImg.setString(2, img.getImagePath());
            pstmtImg.setString(3, item.getId());
            pstmtImg.addBatch();
          }
          pstmtImg.executeBatch();
        }
      }

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
    String sqlItem = "INSERT INTO items (id, title, description, category, seller_id) VALUES (?, ?, ?, ?, ?)";
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
        pstmtItem.setString(5, item.getSeller().getId());
        pstmtItem.executeUpdate();
      }

      // Lưu Danh sách ảnh vào bảng item_images (Dùng Batch)
      List<ItemImage> images = item.getImages();
      if (images != null && !images.isEmpty()) {
        try (PreparedStatement pstmtImage = conn.prepareStatement(sqlImage)) {
          for (ItemImage img : images) {
            pstmtImage.setString(1, img.getId());
            pstmtImage.setString(2, img.getImagePath());
            pstmtImage.setString(3, item.getId());
            // Gom lệnh
            pstmtImage.addBatch();
          }
          // Đẩy 1 lần xuống DB
          pstmtImage.executeBatch();
        }
      }

      // Chốt giao dịch
      conn.commit();
      System.out.println("✅ Đã lưu thành công sản phẩm: " + item.getTitle());

    } catch (SQLException e) {
      System.err.println("❌ Lỗi Giao dịch Lưu Sản Phẩm! Đang Rollback... " + e.getMessage());
      try {
        if (conn != null) conn.rollback();
        System.out.println("🔄 Đã hoàn tác an toàn.");
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi nghiêm trọng khi Rollback: " + ex.getMessage());
      }
    } finally {
      try {
        if(conn !=null) conn.setAutoCommit(true);
      } catch (SQLException e) {
        System.err.println("❌ Lỗi kết nối DB: " + e.getMessage());
      }
    }
  }
}



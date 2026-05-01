package com.nhomX.example.repository;

import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemRepositoryImpl implements ItemRepository {

  // Lấy kết nối Database
  private final Connection conn = DatabaseConnection.getInstance().getConnection();

  // ✅ Nhiệm vụ 1: Lấy toàn bộ danh sách sản phẩm (SELECT *)
  @Override
  public List<Items> findAll() {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items";

    // ✅ Nhiệm vụ 4: Xử lý đóng kết nối an toàn (try-with-resources)
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      while (rs.next()) {
        // Đẩy dữ liệu vào danh sách
        itemsList.add(mapRowToItem(rs));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
    }
    return itemsList;
  }

  // ✅ Nhiệm vụ 2: Lọc sản phẩm theo danh mục (WHERE category = ?)
  @Override
  public List<Items> findByCategory(String category) {
    List<Items> itemsList = new ArrayList<>();
    String sql = "SELECT * FROM items WHERE category = ?";

    // ✅ Nhiệm vụ 4: try-with-resources
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, category); // Truyền tham số category vào dấu ?

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          // Đẩy dữ liệu vào danh sách
          itemsList.add(mapRowToItem(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lọc sản phẩm theo danh mục: " + e.getMessage());
    }
    return itemsList;
  }

  // ✅ Nhiệm vụ 3: Hàm phụ dùng để ánh xạ (map) dữ liệu từ ResultSet vào đối tượng Items
  private Items mapRowToItem(ResultSet rs) throws SQLException {
    GeneralItem item = new GeneralItem(); // Dùng class con để khởi tạo

    // Bạn hãy kiểm tra lại tên cột trong DB và tên hàm set để chỉnh lại cho khớp 100% nhé
    item.setTitle(rs.getString("title"));
    item.setDescription(rs.getString("description"));

    // Nếu Items có các hàm này thì mở comment ra dùng:
    // item.setId(rs.getString("id"));
    // item.setStartingPrice(rs.getDouble("starting_price"));
    // item.setCurrentPrice(rs.getDouble("current_price"));

    return item;
  }
 
   @Override
    public Items findById(String id) {
        return null; // Task này chưa yêu cầu code nên để tạm return null
    }

    @Override
    public void update(Items item) {
        // Tạm thời để trống
    }

    @Override
    public void save(Items item) {
        // Tạm thời để trống
    }
}
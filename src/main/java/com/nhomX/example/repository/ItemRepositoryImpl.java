package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.utils.DatabaseConnection;

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
    item.setImagePath(rs.getString("image_path"));
    item.setId(rs.getString("id"));
    item.setStartingPrice(rs.getDouble("starting_price"));
    item.setCurrentPrice(rs.getDouble("current_price"));

    return item;
  }

  @Override
  public Items findById(String id) {
    // 1. Triển khai logic truy vấn: Câu lệnh SQL tìm 1 bản ghi theo ID
    String sql = "SELECT * FROM items WHERE id = ?";

    // 2 & 3. Xử lý đóng tài nguyên bằng try-with-resources cho PreparedStatement
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền ID người dùng muốn tìm vào dấu ?
      pstmt.setString(1, id);

      // Dùng thêm try-with-resources cho ResultSet để tự động đóng sau khi đọc xong
      try (ResultSet rs = pstmt.executeQuery()) {
        // Nếu rs.next() là true nghĩa là tìm thấy dữ liệu trong Database
        if (rs.next()) {
          // 4. Tái sử dụng code: Dùng hàm mapRowToItem có sẵn để convert dữ liệu
          return mapRowToItem(rs);
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
    String sql =
        "UPDATE items SET title = ?, description = ?, starting_price = ?, current_price = ?, end_time = ?, seller_id = ?, image_path = ? WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, item.getTitle());
      pstmt.setString(2, item.getDescription());
      pstmt.setDouble(3, item.getStartingPrice());
      pstmt.setDouble(4, item.getCurrentPrice());
      pstmt.setString(5, item.getEndTime() != null ? item.getEndTime().toString() : null);
      pstmt.setString(6, item.getSellerId());

      // Cập nhật đường dẫn ảnh mới (hỗ trợ cả NULL và chuỗi nhiều ảnh)
      pstmt.setString(7, item.getImagePath());

      // Điều kiện WHERE id = ? nằm ở vị trí thứ 8
      pstmt.setString(8, item.getId());

      pstmt.executeUpdate(); // Thực thi lệnh cập nhật
      System.out.println("Đã cập nhật thành công sản phẩm: " + item.getTitle());

    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi cập nhật sản phẩm: " + e.getMessage());
    }
  }

  @Override
  public void save(Items item) {
    // Câu lệnh SQL chèn đủ 8 cột (bao gồm cả image_path ở cuối cùng)
    String sql =
        "INSERT INTO items (id, title, description, starting_price, current_price, end_time, seller_id, image_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    // try-with-resources giúp tự động đóng kết nối sau khi chạy xong
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, item.getId());
      pstmt.setString(2, item.getTitle());
      pstmt.setString(3, item.getDescription());
      pstmt.setDouble(4, item.getStartingPrice());
      pstmt.setDouble(5, item.getCurrentPrice());
      // Tùy theo kiểu dữ liệu của end_time trong model mà bạn dùng setString hoặc setDate nhé
      pstmt.setString(6, item.getEndTime() != null ? item.getEndTime().toString() : null);
      pstmt.setString(7, item.getSellerId());

      // ĐÂY LÀ ĐIỂM CHỐT HẠ CỦA TASK NÀY:
      // Truyền image_path vào vị trí dấu ? thứ 8.
      // Nếu item.getImagePath() là null, JDBC sẽ tự động chèn chữ NULL chuẩn của SQL vào DB.
      pstmt.setString(8, item.getImagePath());

      pstmt.executeUpdate(); // Thực thi lệnh chèn
      System.out.println("Đã lưu thành công sản phẩm: " + item.getTitle());

    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu sản phẩm: " + e.getMessage());
    }
  }
}

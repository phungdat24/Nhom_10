package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.nhomX.example.model.Bid;
import com.nhomX.example.utils.DatabaseConnection;

public class BidRepositoryImpl implements BidRepository {
  // Kho chứa ổ khóa: Mỗi ID sản phẩm sẽ tương ứng với 1 ổ khóa riêng biệt
  private static final ConcurrentHashMap<String, ReentrantLock> itemLocks =
      new ConcurrentHashMap<>();

  private final Connection conn = DatabaseConnection.getInstance().getConnection();

  @Override
  public void addBid(Bid bid) { // Đã đổi save -> addBid
    // 1. Lấy ổ khóa ĐỘC QUYỀN cho riêng sản phẩm này (dựa vào itemId)
    ReentrantLock lock = itemLocks.computeIfAbsent(bid.getItemId(), k -> new ReentrantLock());

    // 2. Bấm chốt khóa! Các luồng khác mua cùng sản phẩm sẽ phải đứng chờ ở đây
    lock.lock();

    try {
      String sql =
          "INSERT INTO bids (id, bid_time, user_id, item_id, amount) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, bid.getId());
        pstmt.setString(2, bid.getBidTime() != null ? bid.getBidTime().toString() : null);
        pstmt.setString(3, bid.getUserId());
        pstmt.setString(4, bid.getItemId());
        pstmt.setLong(5, bid.getAmount());

        pstmt.executeUpdate();
        System.out.println("✅ Đã ghi nhận lượt đấu giá thành công!");
      } catch (SQLException e) {
        System.err.println("❌ Lỗi khi lưu lượt đấu giá: " + e.getMessage());
      }
    } finally {
      // 4. BẮT BUỘC: Mở khóa để người tiếp theo trong hàng chờ được vào mua
      lock.unlock();
    }
  }

  @Override
  public List<Bid> getBitsByItemId(String itemId) { // Giữ nguyên lỗi chính tả "Bits" của leader để
                                                    // không bị gạch đỏ
    List<Bid> listBids = new ArrayList<>();
    String sql = "SELECT * FROM bids WHERE item_id = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, itemId);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        Bid bid = new Bid();
        // Đọc dữ liệu từ DB và nhét vào đối tượng Bid
        bid.setId(rs.getString("id"));
        bid.setUserId(rs.getString("user_id"));
        bid.setItemId(rs.getString("item_id"));
        bid.setAmount(rs.getLong("amount"));
        listBids.add(bid);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy danh sách đấu giá: " + e.getMessage());
    }
    return listBids;
  }

  @Override
  public Bid getHighestBid(String itemId) {
    // Dùng ORDER BY amount DESC LIMIT 1 để lấy ra người đặt giá cao nhất
    String sql = "SELECT * FROM bids WHERE item_id = ? ORDER BY amount DESC LIMIT 1";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, itemId);
      ResultSet rs = pstmt.executeQuery();
      if (rs.next()) {
        Bid bid = new Bid();
        bid.setId(rs.getString("id"));
        bid.setUserId(rs.getString("user_id"));
        bid.setItemId(rs.getString("item_id"));
        bid.setAmount(rs.getLong("amount"));
        return bid;
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy giá cao nhất: " + e.getMessage());
    }
    return null;
  }

  @Override
  public boolean placeBidTransaction(String userId, String itemId, long bidAmount, String bidId) {
    Connection conn = null;

    try {
      // BƯỚC 1: Mở một kết nối Database duy nhất
      conn = DatabaseConnection.getInstance().getConnection();

      // BƯỚC 2: Tắt chế độ tự động lưu (Bắt đầu gom các lệnh vào 1 Giao dịch)
      conn.setAutoCommit(false);


      // BƯỚC 3: Mở khối try để thực hiện chuỗi Giao dịch (3 lệnh)
      // Lệnh 1: Trừ tiền (Cập nhật balance)
      String sqlUser = "UPDATE users SET balance = balance - ? WHERE id = ?";
      try (PreparedStatement pstmt1 = conn.prepareStatement(sqlUser)) {
        pstmt1.setLong(1, bidAmount);
        pstmt1.setString(2, userId);
        pstmt1.executeUpdate();
      }

      // Lệnh 2: Thêm lịch sử đấu giá
      String sqlBid = "INSERT INTO bids (id, user_id, item_id, amount) VALUES (?, ?, ?, ?)";
      try (PreparedStatement pstmt2 = conn.prepareStatement(sqlBid)) {
        pstmt2.setString(1, bidId);
        pstmt2.setString(2, userId);
        pstmt2.setString(3, itemId);
        pstmt2.setLong(4, bidAmount);
        pstmt2.executeUpdate();
      }

      // Lệnh 3: Cập nhật giá hiện tại của sản phẩm
      String sqlItem = "UPDATE items SET current_price = ? WHERE id = ?";
      try (PreparedStatement pstmt3 = conn.prepareStatement(sqlItem)) {
        pstmt3.setLong(1, bidAmount);
        pstmt3.setString(2, itemId);
        pstmt3.executeUpdate();
      }

      // BƯỚC 4: Nếu cả 3 lệnh trên chạy trót lọt, gọi chốt sổ!
      conn.commit();
      System.out.println("✅ Giao dịch thành công! Đã chốt sổ dữ liệu.");
      return true;

    } catch (SQLException e) {
      // BƯỚC 5: Mở khối catch - Có lỗi xảy ra, tiến hành quay xe (Rollback)
      System.err.println("❌ Lỗi Giao dịch! Đang hoàn tác (Rollback)... Lý do: " + e.getMessage());
      if (conn != null) {
        try {
          conn.rollback();
          System.out.println("🔄 Đã hoàn tác an toàn. Không ai bị mất tiền oan.");
        } catch (SQLException ex) {
          System.err.println("❌ Lỗi nghiêm trọng khi Rollback: " + ex.getMessage());
        }
      }
      return false;

    } finally {
      // BƯỚC 6: Mở khối finally để dọn dẹp chiến trường
      if (conn != null) {
        try {
          // BƯỚC 7: Trả lại trạng thái mặc định cho Connection
          conn.setAutoCommit(true);
          // BƯỚC 8: Đóng kết nối
          conn.close();
        } catch (SQLException ex) {
          System.err.println("❌ Lỗi khi dọn dẹp kết nối: " + ex.getMessage());
        }
      }
    }
  }
}
package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.nhomX.example.model.Bid;
import com.nhomX.example.utils.DatabaseConnection;

public class BidRepositoryImpl implements BidRepository {

  private final Connection conn = DatabaseConnection.getInstance().getConnection();

  @Override
  public void addBid(Bid bid) { // Đã đổi save -> addBid
    String sql = "INSERT INTO bids (id, bid_time, user_id, item_id, amount) VALUES (?, ?, ?, ?, ?)";
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
}

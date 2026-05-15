package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AutoBidConfig;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AutoBidRepositoryImpl implements AutoBidRepository {

  @Override
  public boolean save(AutoBidConfig config) {
    String sql =
        "INSERT OR REPLACE INTO auto_bids (id, max_limit, increment, created_at, user_id, auction_id) VALUES (?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, config.getId());
      pstmt.setLong(2, config.getMaxLimit());
      pstmt.setLong(3, config.getIncrement());

      String createdAt = config.getCreatedAt() != null ? config.getCreatedAt().toString()
          : LocalDateTime.now().toString();
      pstmt.setString(4, createdAt);

      // ĐÃ SỬA LỖI: Dùng getBidder() thay vì getUser() cho khớp với file AutoBidConfig.java
      pstmt.setString(5, config.getBidder() != null ? config.getBidder().getId() : null);
      pstmt.setString(6, config.getAuction() != null ? config.getAuction().getId() : null);

      pstmt.executeUpdate();
      System.out.println("✅ Đã lưu cấu hình AutoBid thành công!");
      return true;
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu AutoBid: " + e.getMessage());
      return false;
    }
  }

  @Override
  public List<AutoBidConfig> findByAuctionId(String auctionId) {
    List<AutoBidConfig> list = new ArrayList<>();
    String sql = "SELECT * FROM auto_bids WHERE auction_id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAutoBid(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy danh sách AutoBid: " + e.getMessage());
    }
    return list;
  }

  @Override
  public void delete(String configId) {
    String sql = "DELETE FROM auto_bids WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, configId);
      pstmt.executeUpdate();
      System.out.println("🗑️ Đã hủy cấu hình AutoBid: " + configId);
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi xóa AutoBid: " + e.getMessage());
    }
  }

  private AutoBidConfig mapRowToAutoBid(ResultSet rs) throws SQLException {
    AutoBidConfig config = new AutoBidConfig();

    config.setId(rs.getString("id"));
    config.setMaxLimit(rs.getLong("max_limit"));
    config.setIncrement(rs.getLong("increment"));

    String createdAtStr = rs.getString("created_at");
    if (createdAtStr != null && !createdAtStr.isEmpty()) {
      try {
        if (createdAtStr.contains("T")) {
          config.setCreatedAt(LocalDateTime.parse(createdAtStr));
        } else {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          config.setCreatedAt(LocalDateTime.parse(createdAtStr, formatter));
        }
      } catch (Exception e) {
        // Nuốt lỗi an toàn
      }
    }

    String userId = rs.getString("user_id");
    if (userId != null) {
      RegularUser user = new RegularUser();
      user.setId(userId);
      // ĐÃ SỬA LỖI: Dùng setBidder() thay vì setUser()
      config.setBidder(user);
    }

    String auctionId = rs.getString("auction_id");
    if (auctionId != null) {
      Auction auction = new Auction();
      auction.setId(auctionId);
      config.setAuction(auction);
    }

    return config;
  }

  @Override
  public AutoBidConfig findByUserAndAuction(String userId, String auctionId) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'findByUserAndAuction'");
  }

  @Override
  public void deactivate(String configId) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'deactivate'");
  }
}

package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AutoBidConfig;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AutoBidRepositoryImpl implements AutoBidRepository {
  private static final DateTimeFormatter DB_FORMATTER =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public boolean save(AutoBidConfig config) {
    String sql =
        "INSERT OR REPLACE INTO auto_bids "
                + "(id, user_id, auction_id, max_price, step_price, is_active, created_at) "
                + "VALUES (COALESCE((SELECT id FROM auto_bids "
                + "WHERE user_id=? AND auction_id=?), ?), ?, ?, ?, ?, 1, ?)";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, config.getBidder().getId());
      pstmt.setString(2, config.getAuction().getId());
      pstmt.setString(3, UUID.randomUUID().toString()); // id mới nếu chưa tồn tại
      pstmt.setString(4, config.getBidder().getId());
      pstmt.setString(5, config.getAuction().getId());
      pstmt.setLong(6,   config.getMaxLimit());
      pstmt.setLong(7,   config.getIncrement());
      pstmt.setString(8, LocalDateTime.now().format(DB_FORMATTER));

      pstmt.executeUpdate();
      System.out.println("AUTO-BID: Đã lưu config cho user="
              + config.getBidder().getId());
      return true;
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu AutoBid: " + e.getMessage());
      return false;
    }
  }
  // [CHUYỂN VỀ ĐÚNG CHỖ] Lấy danh sách Auto-bid đang bật cho một phiên
  // Loại trừ người vừa thắng bid để tránh tự bid chính mình
  @Override
  public List<AutoBidConfig> findByAuctionId(String auctionId) {
    return findActiveByAuctionId(auctionId, null);
  }
  /**
   * Lấy Auto-bid đang bật (is_active=1), loại trừ một userId cụ thể.
   * Sắp xếp theo max_price DESC: Người trả cao nhất được xử lý trước.
   */
  public List<AutoBidConfig> findActiveByAuctionId(String auctionId, String excludeUserId) {
    List<AutoBidConfig> list = new ArrayList<>();

    // Nếu excludeUserId null thì lấy tất cả, ngược lại loại trừ
    String sql = excludeUserId != null
            ? "SELECT * FROM auto_bids "
            + "WHERE auction_id=? AND is_active=1 AND user_id!=? "
            + "ORDER BY max_price DESC"
            : "SELECT * FROM auto_bids "
            + "WHERE auction_id=? AND is_active=1 "
            + "ORDER BY max_price DESC";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, auctionId);
      if (excludeUserId != null) ps.setString(2, excludeUserId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAutoBid(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("AUTO-BID: Lỗi truy vấn - " + e.getMessage());
    }
    return list;
  }
  // [CHUYỂN VỀ ĐÚNG CHỖ] Tắt Auto-bid khi đạt max_price hoặc phiên kết thúc
  // =========================================================================
  @Override
  public void deactivate(String configId) {
    String sql = "UPDATE auto_bids SET is_active=0 WHERE id=?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, configId);
      ps.executeUpdate();
    } catch (SQLException e) {
      System.err.println("AUTO-BID: Lỗi deactivate - " + e.getMessage());
    }
  }
  /**
   * Tắt theo cặp (userId, auctionId) — dùng khi người dùng tự tắt từ UI.
   */
  public void deactivateByUserAndAuction(String userId, String auctionId) {
    String sql = "UPDATE auto_bids SET is_active=0 "
            + "WHERE user_id=? AND auction_id=?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setString(2, auctionId);
      ps.executeUpdate();
      System.out.println("AUTO-BID: Đã tắt cho user=" + userId
              + " auction=" + auctionId);
    } catch (SQLException e) {
      System.err.println("AUTO-BID: Lỗi deactivate - " + e.getMessage());
    }
  }
  @Override
  public AutoBidConfig findByUserAndAuction(String userId, String auctionId) {
    String sql = "SELECT * FROM auto_bids "
            + "WHERE user_id=? AND auction_id=?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setString(2, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRowToAutoBid(rs);
      }
    } catch (SQLException e) {
      System.err.println("AUTO-BID: Lỗi findByUserAndAuction - " + e.getMessage());
    }
    return null;
  }

  @Override
  public void delete(String configId) {
    String sql = "DELETE FROM auto_bids WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
    config.setMaxLimit(rs.getLong("max_price"));
    config.setIncrement(rs.getLong("step_price"));

    RegularUser user = new RegularUser();
    user.setId(rs.getString("user_id"));
    config.setBidder(user);

    Auction auction = new Auction();
    auction.setId(rs.getString("auction_id"));
    config.setAuction(auction);

    String createdAt = rs.getString("created_at");
    if (createdAt != null) {
      try {
        config.setCreatedAt(createdAt.contains("T")
                ? LocalDateTime.parse(createdAt)
                : LocalDateTime.parse(createdAt, DB_FORMATTER));
      } catch (Exception ignored) {}
    }
    return config;
  }
}

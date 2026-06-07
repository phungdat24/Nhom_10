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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AutoBidConfig;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AutoBidRepositoryImpl implements AutoBidRepository {
  private static final Logger logger = LoggerFactory.getLogger(AutoBidRepositoryImpl.class);
  private static final DateTimeFormatter DB_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public boolean save(AutoBidConfig config) {
    if (config == null
            || config.getBidder() == null
            || config.getBidder().getId() == null
            || config.getAuction() == null
            || config.getAuction().getId() == null) {

      System.err.println("AUTO-BID: Config không hợp lệ, thiếu user hoặc auction.");
      return false;
    }

    String userId = config.getBidder().getId();
    String auctionId = config.getAuction().getId();

    String updateSql =
            "UPDATE auto_bids "
                    + "SET max_price = ?, step_price = ?, is_active = 1, created_at = ? "
                    + "WHERE user_id = ? AND auction_id = ?";

    String insertSql =
            "INSERT INTO auto_bids "
                    + "(id, user_id, auction_id, max_price, step_price, is_active, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, 1, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

      String now = LocalDateTime.now().format(DB_FORMATTER);

      try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
        updateStmt.setLong(1, config.getMaxLimit());
        updateStmt.setLong(2, config.getIncrement());
        updateStmt.setString(3, now);
        updateStmt.setString(4, userId);
        updateStmt.setString(5, auctionId);

        int updatedRows = updateStmt.executeUpdate();

        if (updatedRows > 0) {
          System.out.println("AUTO-BID: Đã cập nhật config cũ. user="
                  + userId + ", auction=" + auctionId);
          return true;
        }
      }

      try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
        insertStmt.setString(1, UUID.randomUUID().toString());
        insertStmt.setString(2, userId);
        insertStmt.setString(3, auctionId);
        insertStmt.setLong(4, config.getMaxLimit());
        insertStmt.setLong(5, config.getIncrement());
        insertStmt.setString(6, now);

        int insertedRows = insertStmt.executeUpdate();

        if (insertedRows > 0) {
          System.out.println("AUTO-BID: Đã thêm config mới. user="
                  + userId + ", auction=" + auctionId);
          return true;
        }
      }

      System.err.println("AUTO-BID: Không insert/update được dòng nào.");
      return false;

    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu AutoBid:");
      System.err.println("userId = " + userId);
      System.err.println("auctionId = " + auctionId);
      System.err.println("maxLimit = " + config.getMaxLimit());
      System.err.println("increment = " + config.getIncrement());
      e.printStackTrace();
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
   * Lấy Auto-bid đang bật (is_active=1), loại trừ một userId cụ thể. Sắp xếp theo max_price DESC:
   * Người trả cao nhất được xử lý trước.
   */
  public List<AutoBidConfig> findActiveByAuctionId(String auctionId, String excludeUserId) {
    List<AutoBidConfig> list = new ArrayList<>();

    // Nếu excludeUserId null thì lấy tất cả, ngược lại loại trừ
    String sql = excludeUserId != null
        ? "SELECT * FROM auto_bids " + "WHERE auction_id=? AND is_active=1 AND user_id!=? "
            + "ORDER BY max_price DESC"
        : "SELECT * FROM auto_bids " + "WHERE auction_id=? AND is_active=1 "
            + "ORDER BY max_price DESC";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, auctionId);
      if (excludeUserId != null)
        ps.setString(2, excludeUserId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAutoBid(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("AUTO-BID: Lỗi truy vấn", e);
    }
    return list;
  }

  // [CHUYỂN VỀ ĐÚNG CHỖ] Tắt Auto-bid khi đạt max_price hoặc phiên kết thúc
  // =========================================================================
  @Override
  public void deactivate(String configId) {
    String sql = "UPDATE auto_bids SET is_active=0 WHERE id=?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, configId);
      ps.executeUpdate();
    } catch (SQLException e) {
      logger.error("AUTO-BID: Lỗi deactivate", e);
    }
  }

  /**
   * Tắt theo cặp (userId, auctionId) — dùng khi người dùng tự tắt từ UI.
   */
  public void deactivateByUserAndAuction(String userId, String auctionId) {
    String sql = "UPDATE auto_bids SET is_active=0 " + "WHERE user_id=? AND auction_id=?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setString(2, auctionId);
      ps.executeUpdate();
      logger.info("AUTO-BID: Đã tắt cho user={} auction={}", userId, auctionId);
    } catch (SQLException e) {
      logger.error("AUTO-BID: Lỗi deactivate", e);
    }
  }

  @Override
  public AutoBidConfig findByUserAndAuction(String userId, String auctionId) {
    String sql = "SELECT * FROM auto_bids " + "WHERE user_id=? AND auction_id=?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.setString(2, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next())
          return mapRowToAutoBid(rs);
      }
    } catch (SQLException e) {
      logger.error("AUTO-BID: Lỗi findByUserAndAuction", e);
    }
    return null;
  }

  @Override
  public void delete(String configId) {
    String sql = "DELETE FROM auto_bids WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, configId);
      pstmt.executeUpdate();
      logger.info("Đã hủy cấu hình AutoBid: {}", configId);
    } catch (SQLException e) {
      logger.error("Lỗi khi xóa AutoBid", e);
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
        config.setCreatedAt(createdAt.contains("T") ? LocalDateTime.parse(createdAt)
            : LocalDateTime.parse(createdAt, DB_FORMATTER));
      } catch (Exception ignored) {
      }
    }
    return config;
  }
}

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
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AuctionRepositoryImpl implements AuctionRepository {

  // Formatter chuẩn để lưu/đọc thời gian nhất quán
  private static final DateTimeFormatter DB_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void save(Auction auction) {
    String sql =
        "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id, winner_id, approved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auction.getId());

      // [FIX] Lưu startingPrice (giá khởi điểm gốc) lấy từ item, không dùng highestBid
      long startingPrice = (auction.getItem() != null) ? auction.getItem().getStartingPrice()
          : auction.getHighestBid();
      pstmt.setLong(2, startingPrice);
      pstmt.setLong(3, auction.getHighestBid());

      // Lưu start_time đúng định dạng:
      pstmt.setString(4,
          auction.getStartTime() != null ? auction.getStartTime().format(DB_FORMATTER) : null);
      pstmt.setString(5,
          auction.getEndTime() != null ? auction.getEndTime().format(DB_FORMATTER) : null);

      // Xử lý Enum
      pstmt.setString(6,
          auction.getStatus() != null ? auction.getStatus().name() : AuctionStatus.PENDING.name());

      pstmt.setString(7, auction.getItem() != null ? auction.getItem().getId() : null);
      pstmt.setString(8, auction.getWinner() != null ? auction.getWinner().getId() : null);

      // Truyền null vì Model không có approvedBy
      pstmt.setString(9, null);

      pstmt.executeUpdate();
      System.out.println("✅ Đã lưu phiên đấu giá: " + auction.getId());
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu Auction: " + e.getMessage());
    }
  }

  @Override
  public Auction findById(String id) {
    String sql = "SELECT * FROM auctions WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, id);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return mapRowToAuction(rs);
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi tìm Auction: " + e.getMessage());
    }
    return null;
  }

  @Override
  public List<Auction> findAllActiveAuctions() {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * FROM auctions WHERE status IN('OPEN', 'RUNNING') ";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {
      while (rs.next()) {
        list.add(mapRowToAuction(rs));
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy ds phiên mở: " + e.getMessage());
    }
    return list;
  }

  @Override
  public List<Auction> findExpiredOpenAuctions() {
    List<Auction> list = new ArrayList<>();
    String now = LocalDateTime.now().toString();
    String sql = "SELECT * FROM auctions WHERE status IN ('OPEN', 'RUNNING') AND end_time <= ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, now);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi quét phiên hết hạn: " + e.getMessage());
    }
    return list;
  }

  @Override
  public void updateStatus(String auctionId, AuctionStatus status) {
    String sql = "UPDATE auctions SET status = ? WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status.name());
      pstmt.setString(2, auctionId);
      pstmt.executeUpdate();
      System.out.println("🔄 Đã cập nhật trạng thái phiên " + auctionId + " thành: " + status);
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật trạng thái: " + e.getMessage());
    }
  }

  @Override
  public void updateHighestBidAndWinner(String auctionId, long newPrice, String winnerId) {
    String sql =
        "UPDATE auctions SET highest_bid = ?, winner_id = ?, status = 'RUNNING' WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setLong(1, newPrice);
      pstmt.setString(2, winnerId);
      pstmt.setString(3, auctionId);
      pstmt.executeUpdate();
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật người thắng: " + e.getMessage());
    }
  }

  @Override
  public void updateEndTime(String auctionId, LocalDateTime newEndTime) {
    String sql = "UPDATE auctions SET end_time = ? WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, newEndTime.format(DB_FORMATTER));
      pstmt.setString(2, auctionId);
      pstmt.executeUpdate();
      System.out.println("⏱ Đã gia hạn thời gian phiên " + auctionId + " → " + newEndTime);
    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật end_time: " + e.getMessage());
    }
  }

  @Override
  public List<Auction> findBySellerId(String sellerId) {
    List<Auction> list = new ArrayList<>();
    // Join với bảng items để lọc theo seller_id
    String sql = "SELECT a.* FROM auctions a " + "JOIN items i ON a.item_id = i.id "
        + "WHERE i.seller_id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, sellerId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy phiên theo seller: " + e.getMessage());
    }
    return list;
  }
  // HÀM PHỤ TRỢ: Map dòng dữ liệu từ DB sang Object

  private Auction mapRowToAuction(ResultSet rs) throws SQLException {
    Auction auction = new Auction();
    auction.setId(rs.getString("id"));

    // Bỏ qua startingPrice, chỉ map highestBid
    auction.setHighestBid(rs.getLong("highest_bid"));

    // Map Enum an toàn
    String statusFromDb = rs.getString("status");
    if (statusFromDb != null) {
      try {
        auction.setStatus(AuctionStatus.valueOf(statusFromDb.toUpperCase()));
      } catch (IllegalArgumentException e) {
        auction.setStatus(AuctionStatus.OPEN);
      }
    }
    auction.setStartTime(parseDateTime(rs.getString("start_time")));
    auction.setEndTime(parseDateTime(rs.getString("end_time")));

    // Gọi ItemRepository để lấy nguyên bộ thông tin chi tiết của món đồ (Tên, Ảnh, Mô tả...)
    String itemId = rs.getString("item_id");
    if (itemId != null) {
      ItemRepository itemRepo = new ItemRepositoryImpl();
      Items fullItem = itemRepo.findById(itemId);
      auction.setItem(fullItem);
    }

    // Tạo vỏ rỗng cho Winner
    String winnerId = rs.getString("winner_id");
    if (winnerId != null) {
      RegularUser winner = new RegularUser();
      winner.setId(winnerId);
      auction.setWinner(winner);
    }

    // Bỏ qua approvedBy vì Model không hỗ trợ

    return auction;
  }

  private LocalDateTime parseDateTime(String timeStr) {
    try {
      if (timeStr.contains("T")) {
        return LocalDateTime.parse(timeStr);
      } else {
        return LocalDateTime.parse(timeStr, DB_FORMATTER);
      }
    } catch (Exception e) {
      System.err.println("⚠️ Lỗi parse ngày giờ: " + timeStr);
      return null;
    }
  }
}

package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.exception.InvalidBidException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class BidRepositoryImpl implements BidRepository {
  // Kho chứa ổ khóa: Mỗi ID sản phẩm sẽ tương ứng với 1 ổ khóa riêng biệt
  private static final ConcurrentHashMap<String, ReentrantLock> auctionLocks =
      new ConcurrentHashMap<>();
  private static final DateTimeFormatter DB_FORMATTER =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void addBid(BidTransaction bidTransaction) { // Đã đổi save -> addBid
    // 1. Lấy ổ khóa ĐỘC QUYỀN cho riêng sản phẩm này (dựa vào itemId)
    ReentrantLock lock =
        auctionLocks.computeIfAbsent(bidTransaction.getAuction().getId(), k -> new ReentrantLock());

    // 2. Bấm chốt khóa! Các luồng khác mua cùng sản phẩm sẽ phải đứng chờ ở đây
    lock.lock();
    // SỬ DỤNG TRY-WITH-RESOURCES ĐỂ TỰ ĐỘNG ĐÓNG KẾT NỐI
    String sql =
        "INSERT INTO bids (id, amount, bid_time, user_id, auction_id) VALUES (?, ?, ?, ?, ?)";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, bidTransaction.getId());
      pstmt.setLong(2, bidTransaction.getAmount());
      pstmt.setString(3,
          bidTransaction.getBidTime() != null ? bidTransaction.getBidTime().toString() : null);
      pstmt.setString(4, bidTransaction.getBidder().getId());
      pstmt.setString(5, bidTransaction.getAuction().getId());

      pstmt.executeUpdate();
      System.out.println("✅ Đã ghi nhận lượt đấu giá thành công!");
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu lượt đấu giá: " + e.getMessage());
    } finally {
      // Mở khóa để người tiếp theo trong hàng chờ được vào mua
      lock.unlock();
    }
  }

  @Override
  public List<BidTransaction> getBidsByAuctionId(String auctionId) {

    List<BidTransaction> listBidTransactions = new ArrayList<>();
    // JOIN 2 bảng lại với nhau để lấy Fullname ra
    String sql = "SELECT b.*, u.fullname, u.username " +
            "FROM bids b " +
            "JOIN users u ON b.user_id = u.id " +
            "WHERE b.auction_id = ? ORDER BY b.bid_time ASC";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {

        while (rs.next()) {
          listBidTransactions.add(mapRowToBidWithFullname(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy danh sách đấu giá: " + e.getMessage());
    }
    return listBidTransactions;
  }

  @Override
  public BidTransaction getHighestBid(String auctionId) {
    // Dùng ORDER BY amount DESC LIMIT 1 để lấy ra người đặt giá cao nhất
    String sql = "SELECT * FROM bids WHERE auction_id = ? ORDER BY amount DESC LIMIT 1";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);

      try (ResultSet rs = pstmt.executeQuery()) {

        if (rs.next()) {
          return mapRowToBid(rs);
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy giá cao nhất: " + e.getMessage());
    }
    return null;
  }

  @Override
  public boolean executeBidTransaction(String userId, String auctionId, long bidAmount, String bidId) {

    Connection conn = DatabaseConnection.getInstance().getConnection();
    // [FIX] Dùng lock theo auctionId để đảm bảo thread-safety trong transaction
    ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());
    lock.lock();
    // Bắt lỗi rollback:
    try {

      // BƯỚC 2: Tắt chế độ tự động lưu (Bắt đầu gom các lệnh vào 1 Giao dịch)
      conn.setAutoCommit(false);
      // =========================================================
      // TRẠM 1: KIỂM DUYỆT TỪ SERVER (Zero-Trust Validation)
      // =========================================================
      String sqlCheckAuction = "SELECT highest_bid, winner_id, status, end_time FROM auctions WHERE id = ?";
      // Gía cao nhất hiện tại
      long currentHighestBid = 0;
      // Người dẫn đầu cũ
      String oldWinnerId = null;
      //Thời gian kết thúc
      LocalDateTime endTime = null;

      try (PreparedStatement pstmtAuction = conn.prepareStatement(sqlCheckAuction)) {
        pstmtAuction.setString(1, auctionId);
        try (ResultSet rs = pstmtAuction.executeQuery()) {
          if (rs.next()) {
            String status = rs.getString("status");
            currentHighestBid = rs.getLong("highest_bid");
            oldWinnerId = rs.getString("winner_id");

            String endTimeStr = rs.getString("end_time");
            endTime = (endTimeStr != null) ? LocalDateTime.parse(endTimeStr,DB_FORMATTER) : null;

            // Kiểm tra 1: Có đang mở bán không?
            if (!"RUNNING".equals(status) && !"OPEN".equals(status)) {
              // Java Database Transaction mặc định chỉ tự động Rollback khi gặp RuntimeException
              throw new AuctionClosedException("Phiên đấu giá đã đóng hoặc chưa bắt đầu!");
            }

            // Kiểm tra 2: Còn hạn không?
            if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
              throw new AuctionClosedException("Phiên đấu giá đã kết thúc thời gian!");
            }

            // Kiểm tra 3: Giá đấm búa có thực sự lớn hơn giá DB hiện tại không?
            if (bidAmount <= currentHighestBid) {
              throw new InvalidBidException("Giá đặt " + bidAmount + " không lớn hơn giá trần hiện tại " + currentHighestBid);
            }
          } else {
            System.err.println("❌ Không tìm thấy mã phiên đấu giá!");
            conn.rollback();
            return false;
          }
        }
      }

      // =========================================================
      // TRẠM 2: KIỂM TRA SỐ DƯ TÀI KHOẢN NGƯỜI MUA MỚI
      // =========================================================
      String sqlCheckBalance = "SELECT balance FROM users WHERE id = ?";
      try (PreparedStatement pstmtCheck = conn.prepareStatement(sqlCheckBalance)) {
        pstmtCheck.setString(1, userId);
        try (ResultSet rs = pstmtCheck.executeQuery()) {
          if (rs.next()) {
            long currentBalance = rs.getLong("balance");
            if (currentBalance < bidAmount) {
              throw new InvalidBidException("Số dư không đủ để đặt giá!");
            }
          } else {
            System.err.println("❌ Không tìm thấy user ID!");
            conn.rollback();
            return false;
          }
        }
      }

      // =========================================================
      // TRẠM 3: HOÀN TIỀN CHO NGƯỜI DẪN ĐẦU CŨ (Refund Logic)
      // =========================================================
      if (oldWinnerId != null && !oldWinnerId.trim().isEmpty() && currentHighestBid > 0) {
        String sqlRefund = "UPDATE users SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement pstmtRefund = conn.prepareStatement(sqlRefund)) {
          pstmtRefund.setLong(1, currentHighestBid);
          pstmtRefund.setString(2, oldWinnerId);
          int rows = pstmtRefund.executeUpdate();
          if (rows > 0) {
            System.out.println("💸 Đã hoàn trả " + currentHighestBid + " cho user cũ: " + oldWinnerId);
          }
        }
      }

      // =========================================================
      // TRẠM 4: TRỪ TIỀN NGƯỜI DẪN ĐẦU MỚI
      // =========================================================
      String sqlDeduct = "UPDATE users SET balance = balance - ? WHERE id = ?";
      try (PreparedStatement pstmtDeduct = conn.prepareStatement(sqlDeduct)) {
        pstmtDeduct.setLong(1, bidAmount);
        pstmtDeduct.setString(2, userId);
        pstmtDeduct.executeUpdate();
      }

      // =========================================================
      // TRẠM 5: ĐẠO LUẬT CHỐNG BẮN TỈA (Anti-Sniping)
      // =========================================================
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime newEndTime = endTime; // Mặc định giữ nguyên giờ cũ

      if (endTime != null) {
        long minutesLeft = java.time.Duration.between(now, endTime).toMinutes();
        if (minutesLeft < 5) {
          newEndTime = now.plusMinutes(5); // Cộng thêm 5 phút từ thời điểm hiện tại
          System.out.println("⏳ Kích hoạt Anti-Sniping: Gia hạn phiên đấu giá tới " + newEndTime);
        }
      }
      String formattedEndTime = (newEndTime != null) ? newEndTime.format(DB_FORMATTER) : null;

      // =========================================================
      // TRẠM 6: LƯU LỊCH SỬ VÀ CẬP NHẬT PHIÊN ĐẤU GIÁ
      // =========================================================
      // 6.1 Thêm lịch sử (Bids)
      String sqlBid = "INSERT INTO bids (id, amount, bid_time, user_id, auction_id) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement pstmtBid = conn.prepareStatement(sqlBid)) {
        pstmtBid.setString(1, bidId);
        pstmtBid.setLong(2, bidAmount);
        pstmtBid.setString(3, now.format(DB_FORMATTER));
        pstmtBid.setString(4, userId);
        pstmtBid.setString(5, auctionId);
        pstmtBid.executeUpdate();
      }

      // 6.2 Cập nhật trạng thái phiên (Auctions)
      String sqlAuctionUpdate = "UPDATE auctions SET highest_bid = ?, winner_id = ?, status = 'RUNNING', end_time = ? WHERE id = ?";
      try (PreparedStatement pstmtAuctionUpdate = conn.prepareStatement(sqlAuctionUpdate)) {
        pstmtAuctionUpdate.setLong(1, bidAmount);
        pstmtAuctionUpdate.setString(2, userId);
        pstmtAuctionUpdate.setString(3, formattedEndTime);
        pstmtAuctionUpdate.setString(4, auctionId);
        pstmtAuctionUpdate.executeUpdate();
      }

      // TẤT CẢ ĐỀU TRƠN TRU -> CHỐT GIAO DỊCH
      conn.commit();
      System.out.println("✅ Giao dịch thành công! Người dùng " + userId + " đang dẫn đầu với " + bidAmount);
      return true;

    } catch (SQLException e) {
      // CÓ LỖI XẢY RA -> QUAY XE TOÀN BỘ
      System.err.println("❌ Lỗi SQL nghiêm trọng! Đang hoàn tác (Rollback)... Lý do: " + e.getMessage());
      try {
        conn.rollback();
        System.out.println("🔄 Đã hoàn tác an toàn.");
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi Rollback: " + ex.getMessage());
      }
      return false;
    } finally {
      // MỞ KHÓA VÀ TRẢ LẠI CHẾ ĐỘ AUTO-COMMIT CHO HỆ THỐNG
      try {
        conn.setAutoCommit(true);
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi khi khôi phục AutoCommit: " + ex.getMessage());
      }
      lock.unlock();
    }
  }

  @Override
  public boolean saveAutoBidConfig(String userId, String auctionId, long maxLimit, long increment) {
    return false;
  }

  private BidTransaction mapRowToBid(ResultSet rs) throws SQLException {
    BidTransaction bid = new BidTransaction();
    bid.setId(rs.getString("id"));
    bid.setAmount(rs.getLong("amount"));
    bid.setBidTime(parseDateTime(rs.getString("bid_time")));

    // "Vỏ rỗng" – load đầy đủ khi cần qua UserRepository
    RegularUser bidder = new RegularUser();
    bidder.setId(rs.getString("user_id"));
    bid.setBidder(bidder);

    Auction auction = new Auction();
    auction.setId(rs.getString("auction_id"));
    bid.setAuction(auction);

    return bid;
  }

  private LocalDateTime parseDateTime(String timeStr) {
    if (timeStr == null || timeStr.trim().isEmpty()) return null;
    try {
      return timeStr.contains("T")
              ? LocalDateTime.parse(timeStr)
              : LocalDateTime.parse(timeStr, DB_FORMATTER);
    } catch (Exception e) {
      System.err.println("⚠️ Lỗi parse thời gian: [" + timeStr + "]");
      return null;
    }
  }
  // THÊM HÀM NÀY XUỐNG CUỐI LỚP BidRepositoryImpl
  private BidTransaction mapRowToBidWithFullname(ResultSet rs) throws SQLException {
    // 1. Tận dụng hàm cũ để lấy các thông số cơ bản (id, amount, time)
    BidTransaction bid = mapRowToBid(rs);

    // 2. Bơm thêm Dữ liệu Fullname vừa JOIN được vào
    bid.getBidder().setUserName(rs.getString("username"));
    bid.getBidder().setFullName(rs.getString("fullname"));

    return bid;
  }
}

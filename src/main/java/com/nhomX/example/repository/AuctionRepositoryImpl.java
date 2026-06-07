package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.MyAuctionStatus;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AuctionRepositoryImpl implements AuctionRepository {
  private static final Logger logger = LoggerFactory.getLogger(AuctionRepositoryImpl.class);

  // Formatter chuẩn để lưu/đọc thời gian nhất quán
  private static final DateTimeFormatter DB_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // ======================================================================
  // ========= NHÓM CÂU LỆNH CRUD(THAO TÁC DỮ LIỆU)========================

  // Khởi tạo và lưu một phiên đấu giá mới vào cơ sở dữ liệu với kết nối độc lập
  @Override
  public void save(Auction auction) {
    // Câu lệnh SQL với tham số ẩn (?) để tránh SQL Injection
    String sql =
        "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id, winner_id, approved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Sử dụng try-with-resources để tự động đóng Statement sau khi dùng xong
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền dữ liệu số và chuỗi cơ bản
      pstmt.setString(1, auction.getId());
      pstmt.setLong(2, auction.getStartingPrice());
      pstmt.setLong(3, auction.getHighestBid());

      // Lưu start_time đúng định dạng: ép kiểu LocalDatetime sang String theo định dạng
      // DB_Formatter
      pstmt.setString(4,
          auction.getStartTime() != null ? auction.getStartTime().format(DB_FORMATTER) : null);
      pstmt.setString(5,
          auction.getEndTime() != null ? auction.getEndTime().format(DB_FORMATTER) : null);

      // Xử lý Enum lấy ở dạng chỗi, null thì gán mặc định là enum
      pstmt.setString(6,
          auction.getStatus() != null ? auction.getStatus().name() : AuctionStatus.PENDING.name());
      // Lây ID của khóa ngoại (Item và winner), đề phòng NullPointerException
      pstmt.setString(7, auction.getItem() != null ? auction.getItem().getId() : null);
      pstmt.setString(8, auction.getWinner() != null ? auction.getWinner().getId() : null);

      // Lưu thông tin người duyệt (approved_by)
      pstmt.setString(9, auction.getApprovedBy());
      // Thực thi lệnh ghi xuống database:
      pstmt.executeUpdate();
      logger.info("Đã lưu phiên đấu giá: {}", auction.getId());
    } catch (SQLException e) {
      logger.error("Lỗi khi lưu Auction", e);
    }
  }

  // Thêm mới một phiên đấu giá cho phép truyền kết nối từ bên ngoài vào
  public void save(Auction auction, Connection conn) throws SQLException {
    String sql =
        "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id, winner_id, approved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, auction.getId());
      pstmt.setLong(2, auction.getStartingPrice());
      pstmt.setLong(3, auction.getHighestBid());
      pstmt.setString(4,
          auction.getStartTime() != null ? auction.getStartTime().format(DB_FORMATTER) : null);
      pstmt.setString(5,
          auction.getEndTime() != null ? auction.getEndTime().format(DB_FORMATTER) : null);
      pstmt.setString(6,
          auction.getStatus() != null ? auction.getStatus().name() : AuctionStatus.PENDING.name());
      pstmt.setString(7, auction.getItem() != null ? auction.getItem().getId() : null);
      pstmt.setString(8, auction.getWinner() != null ? auction.getWinner().getId() : null);
      pstmt.setString(9, auction.getApprovedBy());
      pstmt.executeUpdate();
    }
  }

  // Cập nhật trạng thái của phiên đấu giá theo ID.
  @Override
  public void updateStatus(String auctionId, AuctionStatus status) {
    String sql = "UPDATE auctions SET status = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status.name());
      pstmt.setString(2, auctionId);
      pstmt.executeUpdate();
      logger.info("Đã cập nhật trạng thái phiên {} thành: {}", auctionId, status);
    } catch (SQLException e) {
      logger.error("Lỗi cập nhật trạng thái", e);
    }
  }

  // Cập nhật bước giá cao nhất hiện tại, ID người đang dẫn đầu và chuyển trạng thái phiên sang
  // RUNNING
  @Override
  public void updateHighestBidAndWinner(String auctionId, long newPrice, String winnerId) {
    String sql =
        "UPDATE auctions SET highest_bid = ?, winner_id = ?, status = 'RUNNING' WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setLong(1, newPrice);
      pstmt.setString(2, winnerId);
      pstmt.setString(3, auctionId);
      pstmt.executeUpdate();
    } catch (SQLException e) {
      logger.error("Lỗi cập nhật người thắng", e);
    }
  }

  // Gia hạn thời gian kết thúc (thường dùng trong tính năng chống bắn tỉa - Anti-sniping).
  @Override
  public void updateEndTime(String auctionId, LocalDateTime newEndTime) {
    String sql = "UPDATE auctions SET end_time = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, newEndTime.format(DB_FORMATTER));
      pstmt.setString(2, auctionId);
      pstmt.executeUpdate();
      logger.info("Đã gia hạn thời gian phiên {} -> {}", auctionId, newEndTime);
    } catch (SQLException e) {
      logger.error("Lỗi cập nhật end_time", e);
    }
  }

  // Cập nhật tổng hợp gồm trạng thái, thời gian kết thúc và người phê duyệt của một thực thể
  // Auction
  @Override
  public boolean updateAuctionStatus(Auction auction) {
    // Cập nhật cả status và end_time (phòng trường hợp gia hạn do Anti-sniping)
    String sql = "UPDATE auctions SET status = ?, end_time = ?, approved_by = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auction.getStatus().name());
      pstmt.setString(2,
          auction.getEndTime() != null ? auction.getEndTime().format(DB_FORMATTER) : null);
      pstmt.setString(3, auction.getApprovedBy());
      pstmt.setString(4, auction.getId());

      int affectedRows = pstmt.executeUpdate();
      return affectedRows > 0;

    } catch (SQLException e) {
      logger.error("Lỗi cập nhật trạng thái phiên", e);
      return false;
    }
  }

  // ===== BO SUNG VAO TANG REPOSITORY (Ben Server) =====
  @Override
  public boolean settleAuctionPayment(String auctionId) {
    String sqlSelect = "SELECT a.highest_bid, a.winner_id, a.status, i.seller_id "
        + "FROM auctions a JOIN items i ON a.item_id = i.id WHERE a.id = ?";
    String sqlPay = "UPDATE users SET balance = balance + ? WHERE id = ?";
    String sqlUpdate = "UPDATE auctions SET status = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      conn.setAutoCommit(false); // Bat dau Transaction
      try {
        long highestBid = 0;
        String sellerId = null;
        String winnerId = null;
        String currentStatus = null;

        try (PreparedStatement pstSelect = conn.prepareStatement(sqlSelect)) {
          pstSelect.setString(1, auctionId);
          try (ResultSet rs = pstSelect.executeQuery()) {
            if (!rs.next()) {
              rollbackSilently(conn);
              return false;
            }
            highestBid = rs.getLong("highest_bid");
            sellerId = rs.getString("seller_id");
            winnerId = rs.getString("winner_id");
            currentStatus = rs.getString("status");
          }
        }

        if (AuctionStatus.PAID.name().equalsIgnoreCase(currentStatus)
            || AuctionStatus.CANCELED.name().equalsIgnoreCase(currentStatus)) {
          conn.commit();
          return true;
        }

        boolean hasWinner = winnerId != null && !winnerId.isBlank();
        boolean hasSeller = sellerId != null && !sellerId.isBlank();
        AuctionStatus nextStatus;

        // Neu co nguoi thang va co tien -> Cong tien cho Seller.
        if (hasWinner && hasSeller) {
          try (PreparedStatement pstPay = conn.prepareStatement(sqlPay)) {
            pstPay.setLong(1, highestBid);
            pstPay.setString(2, sellerId);
            if (pstPay.executeUpdate() == 0) {
              throw new SQLException("Khong tim thay seller de cong tien: " + sellerId);
            }
          }
          // Cap nhat trang thai thanh PAID.
          nextStatus = AuctionStatus.PAID;
        } else {
          // Neu khong ai mua, chuyen thanh CANCELED.
          nextStatus = AuctionStatus.CANCELED;
        }

        try (PreparedStatement pstUpdate = conn.prepareStatement(sqlUpdate)) {
          pstUpdate.setString(1, nextStatus.name());
          pstUpdate.setString(2, auctionId);
          pstUpdate.executeUpdate();
        }

        conn.commit();
        return true;
      } catch (SQLException e) {
        rollbackSilently(conn);
        logger.error("Lỗi tất toán phiên đấu giá {}", auctionId, e);
        return false;
      } finally {
        restoreAutoCommit(conn);
      }
    } catch (SQLException e) {
      logger.error("Lỗi mở kết nối DB khi tất toán", e);
      return false;
    }
  }

  // ====== NHÓM TRUY VẤN DỮ LIỆU ĐƠN LẺ VÀ DANH SÁCH===========
  // ===========================================================
  @Override
  public List<Auction> findLiveAuctions() {
    List<Auction> list = new ArrayList<>();
    // Lọc các phiên đang ở trạng thái chuẩn bị hoặc đang diễn ra
    String sql = "SELECT * FROM auctions WHERE status IN ('PENDING', 'UP_COMING', 'OPEN', 'RUNNING')";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql);
         ResultSet rs = pstmt.executeQuery()) {

      while (rs.next()) {
        list.add(mapRowToAuction(rs));
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lấy danh sách Live Auctions cho Admin", e);
    }
    return list;
  }
  // ======================================================================
  // HÀM CHO ADMIN: HỦY ÉP BUỘC VÀ HOÀN TIỀN (FORCE CANCEL & REFUND)
  // ======================================================================
  @Override
  public boolean cancelAuction(String auctionId) {
    String sqlSelect = "SELECT highest_bid, winner_id, status FROM auctions WHERE id = ?";
    String sqlRefund = "UPDATE users SET balance = balance + ? WHERE id = ?";
    String sqlUpdate = "UPDATE auctions SET status = 'CANCELED' WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      // 1. Tắt Auto-commit để gom nhóm lệnh (Đảm bảo an toàn tài chính)
      conn.setAutoCommit(false);

      try {
        long highestBid = 0;
        String winnerId = null;
        String currentStatus = null;

        // BƯỚC 1: Lấy thông tin phiên đấu giá hiện tại
        try (PreparedStatement pstSelect = conn.prepareStatement(sqlSelect)) {
          pstSelect.setString(1, auctionId);
          try (ResultSet rs = pstSelect.executeQuery()) {
            if (!rs.next()) {
              rollbackSilently(conn);
              return false; // Không tìm thấy phiên
            }
            highestBid = rs.getLong("highest_bid");
            winnerId = rs.getString("winner_id");
            currentStatus = rs.getString("status");
          }
        }

        // Kiểm tra an toàn: Nếu đã bị hủy hoặc đã hoàn tất thanh toán rồi thì chặn lại
        if (AuctionStatus.CANCELED.name().equalsIgnoreCase(currentStatus)
                || AuctionStatus.PAID.name().equalsIgnoreCase(currentStatus)
                || AuctionStatus.FINISHED.name().equalsIgnoreCase(currentStatus)) {
          rollbackSilently(conn);
          return false;
        }

        // BƯỚC 2: Hoàn tiền cho người dùng đang giữ Top 1 (Nếu có)
        // Khi người dùng đặt giá, hệ thống đã trừ tiền của họ. Giờ Admin hủy, phải trả lại.
        if (winnerId != null && !winnerId.isBlank() && highestBid > 0) {
          try (PreparedStatement pstRefund = conn.prepareStatement(sqlRefund)) {
            pstRefund.setLong(1, highestBid);
            pstRefund.setString(2, winnerId);
            pstRefund.executeUpdate();
            logger.info("ADMIN FORCE CANCEL: Đã hoàn trả {} VNĐ cho user {}", highestBid, winnerId);
          }
        }

        // BƯỚC 3: Cập nhật trạng thái phiên thành CANCELED
        try (PreparedStatement pstUpdate = conn.prepareStatement(sqlUpdate)) {
          pstUpdate.setString(1, auctionId);
          pstUpdate.executeUpdate();
        }

        // 4. Mọi thứ trơn tru -> Chốt giao dịch lưu xuống ổ cứng
        conn.commit();
        logger.info("ADMIN FORCE CANCEL: Hủy thành công phiên {}", auctionId);
        return true;

      } catch (SQLException e) {
        // Có bất kỳ lỗi gì xảy ra -> Hủy bỏ toàn bộ giao dịch, không hoàn tiền, không đổi trạng thái
        rollbackSilently(conn);
        logger.error("Lỗi Transaction khi Admin hủy phiên đấu giá {}", auctionId, e);
        return false;
      } finally {
        // Phục hồi lại kết nối về trạng thái bình thường
        restoreAutoCommit(conn);
      }
    } catch (SQLException e) {
      logger.error("Lỗi mở kết nối DB khi hủy phiên", e);
      return false;
    }
  }
  // Tìm kiếm thông tin chi tiết một phiên đấu giá bằng id:
  @Override
  public Auction findById(String id) {
    String sql = "SELECT * FROM auctions WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, id);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return mapRowToAuction(rs);
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi tìm Auction", e);
    }
    return null;
  }

  // Danh sách các phiên đấu giá đang diễn ra va có trạng thái hợp lệ:
  @Override
  public List<Auction> findAllActiveAuctions() {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * " + "FROM auctions " + "WHERE status IN('OPEN', 'RUNNING') "
        + "AND start_time <= ? " + "AND end_time > ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      String now = LocalDateTime.now().format(DB_FORMATTER);

      pstmt.setString(1, now); // start_time <= now
      pstmt.setString(2, now); // end_time > now
      try (ResultSet rs = pstmt.executeQuery()) { // ← Query sau khi đã set tham số
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lấy danh sách phiên mở", e);
    }
    return list;
  }

  // Tìm các phiên đã quá giờ kết thúc nhưng trạng thái vẫn chưa được cập nhật đóng.
  @Override
  public List<Auction> findExpiredOpenAuctions() {
    List<Auction> list = new ArrayList<>();
    String now = LocalDateTime.now().format(DB_FORMATTER);
    String sql = "SELECT * FROM auctions WHERE status IN ('OPEN', 'RUNNING') AND end_time <= ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, now);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi quét phiên hết hạn", e);
    }
    return list;
  }


  @Override
  public List<Auction> findBySellerId(String sellerId) {
    List<Auction> list = new ArrayList<>();
    // Join với bảng items để lọc theo seller_id
    String sql = "SELECT a.* FROM auctions a " + "JOIN items i ON a.item_id = i.id "
        + "WHERE i.seller_id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, sellerId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lấy phiên theo seller", e);
    }
    return list;
  }

  // Tìm các phiên đang ở trạng thái chờ (UP_COMING) nhưng đã đến giờ khai mạc để hệ thống kích hoạt
  // kích mở tự động.
  @Override
  public List<Auction> findReadyToOpenAuctions() {
    List<Auction> list = new ArrayList<>();
    // Tìm các phiên đang CHỜ, nhưng thời gian hiện tại đã vượt qua giờ BẮT ĐẦU
    String sql = "SELECT * FROM auctions WHERE status = 'UP_COMING' AND start_time <= ?";

    // Sử dụng try-with-resources để tự động đóng kết nối, chuẩn Checkstyle

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      String now = java.time.LocalDateTime.now().format(DB_FORMATTER);
      pstmt.setString(1, now);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi quét phiên UP_COMING", e);
    }

    return list;
  }

  // Tìm phiên theo trạng thái
  @Override
  public List<Auction> findAuctionsByStatus(AuctionStatus auctionStatus) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * FROM auctions WHERE status = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionStatus.name());
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi tìm Auction theo status", e);
    }
    return list;
  }
  // Tìm kiếm thông tin phiên đấu giá bằng item_id:
  @Override
  public Auction getAuctionByItemId(String itemId) {
    String sql = "SELECT * FROM auctions WHERE item_id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, itemId);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          // Tái sử dụng hàm mapRowToAuction có sẵn của em
          return mapRowToAuction(rs);
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi tìm Auction theo item_id", e);
    }
    return null;
  }
  // =====================================================================
  // NHÓM CÁC CÂU LỆNH TÍNH NĂNG ĐĂ THÙ VÀ THỐNG KÊ:
  // =========================================================================

  // HÀM LẤY DANH SÁCH "MY AUCTIONS" CỦA RIÊNG USER ĐANG ĐĂNG NHẬP
  @Override
  public List<MyAuctionDTO> getMyAuctions(String userId) {
    List<MyAuctionDTO> list = new ArrayList<>();

    // SQL: Lấy thông tin phiên đấu giá + Giá cao nhất mà userId này từng đặt
    // Lưu ý: Tên bảng 'bids' và cột 'bidder_id', 'amount' có thể thay đổi tùy Database thực tế của
    // em.
    String sql = "SELECT a.*, MAX(b.amount) AS my_highest_bid " + "FROM auctions a "
        + "JOIN bids b ON a.id = b.auction_id " + "WHERE b.user_id = ? " + "GROUP BY a.id "
        + "ORDER BY a.end_time DESC";


    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, userId);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          // 1. Tận dụng hàm phụ trợ cũ để lấy thông tin lõi của Auction
          Auction auction = mapRowToAuction(rs);

          // 2. Lấy mức giá cao nhất của RIÊNG user này từ kết quả MAX(b.amount)
          long myHighestBid = rs.getLong("my_highest_bid");

          // 3. LOGIC SO SÁNH TRẠNG THÁI (Đang dẫn đầu / Bị vượt / Thắng / Thua)
          MyAuctionStatus myStatus;
          boolean isClosed = (auction.getStatus() == AuctionStatus.FINISHED
              || auction.getStatus() == AuctionStatus.PAID);

          if (isClosed) {
            // Nếu phiên đã đóng, kiểm tra xem ID người thắng có phải ID của mình không
            if (auction.getWinner() != null && userId.equals(auction.getWinner().getId())) {
              myStatus = MyAuctionStatus.WON;
            } else {
              myStatus = MyAuctionStatus.LOST;
            }
          } else {
            // Nếu phiên đang chạy, so sánh Giá của mình với Giá trần hiện tại
            if (myHighestBid >= auction.getHighestBid()) {
              myStatus = MyAuctionStatus.LEADING; // Bằng hoặc hơn giá trần -> Đang dẫn đầu
            } else {
              myStatus = MyAuctionStatus.OUTBID; // Thấp hơn giá trần -> Đã bị người khác vượt
            }
          }

          // 4. Đóng gói vào DTO và ném vào danh sách
          MyAuctionDTO dto = new MyAuctionDTO(auction, myHighestBid, myStatus);
          list.add(dto);
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi khi lấy danh sách My Auctions", e);
    }
    return list;
  }

  // ---------------- TASK 1: CHIẾN LƯỢC LẤY DANH SÁCH ----------------
  // Lấy danh sách các phiên sắp kết thúc xếp theo thời gian sớm nhất để hiển thị ở trang chủ
  @Override
  public List<Auction> getEndingSoonAuctions(int limit) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * FROM auctions " + "WHERE status IN ('OPEN', 'RUNNING') AND end_time > ? "
        + "ORDER BY end_time ASC LIMIT ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      // Dấu ? số 1 là thời gian hiện tại (Format chuẩn DB của nhóm bạn)
      pstmt.setString(1, java.time.LocalDateTime.now().format(DB_FORMATTER));
      pstmt.setInt(2, limit);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi getTrendingAuctions", e);
    }
    return list;
  }

  // Lấy các phiên "hot" nhất dựa trên số lượng lượt bid (đặt giá) nhiều nhất.
  @Override
  public List<Auction> getTrendingAuctions(int limit) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT a.* FROM auctions a " + "LEFT JOIN bids b ON a.id = b.auction_id "
        + "WHERE a.status = 'RUNNING' " + "GROUP BY a.id " + "ORDER BY COUNT(b.id) DESC LIMIT ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setInt(1, limit);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("Lỗi getTrendingAuctions", e);
    }
    return list;
  }
  // Đếm ca phiên đang chạy

  @Override
  public int countActiveAuctions() {
    int count = 0;
    String sql = "SELECT COUNT(*) FROM auctions WHERE status IN ('OPEN', 'RUNNING')";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      if (rs.next())
        count = rs.getInt(1);
    } catch (SQLException e) {
      logger.error("Lỗi countActiveAuctions", e);
    }
    return count;
  }

  // Đếm các phiên sắp kết thúc
  @Override
  public int countEndingSoonAuctions() {
    int count = 0;
    String sql = "SELECT COUNT(*) FROM auctions " + "WHERE status IN ('OPEN', 'RUNNING') "
        + "AND end_time > ? AND end_time <= ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      LocalDateTime now = java.time.LocalDateTime.now();
      LocalDateTime tomorrow = now.plusHours(24); // Sắp kết thúc trong 24h

      pstmt.setString(1, now.format(DB_FORMATTER));
      pstmt.setString(2, tomorrow.format(DB_FORMATTER));

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next())
          count = rs.getInt(1);
      }
    } catch (SQLException e) {
      logger.error("Lỗi countEndingSoonAuctions", e);
    }
    return count;
  }
  // HÀM PHỤ TRỢ: Chuyển đổi dữ liệu từ dòng hiện tại của ResultSet thành đối tượng Java Auction

  private Auction mapRowToAuction(ResultSet rs) throws SQLException {
    Auction auction = new Auction();
    auction.setId(rs.getString("id"));
    auction.setStartingPrice(rs.getLong("starting_price"));
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
    auction.setApprovedBy(rs.getString("approved_by"));
    return auction;
  }

  // Chuyển đổi chuỗi String ngày tháng từ DB về đối tượng LocalDateTime
  private LocalDateTime parseDateTime(String timeStr) {
    try {
      if (timeStr.contains("T")) {
        return LocalDateTime.parse(timeStr);
      } else {
        return LocalDateTime.parse(timeStr, DB_FORMATTER);
      }
    } catch (Exception e) {
      logger.warn("Lỗi parse ngày giờ: {}", timeStr);
      return null;
    }
  }

  private void rollbackSilently(Connection conn) {
    try {
      if (conn != null) {
        conn.rollback();
      }
    } catch (SQLException ex) {
      logger.error("Lỗi rollback", ex);
    }
  }

  private void restoreAutoCommit(Connection conn) {
    try {
      if (conn != null) {
        conn.setAutoCommit(true);
      }
    } catch (SQLException ex) {
      logger.error("Lỗi restoreAutoCommit", ex);
    }
  }
}

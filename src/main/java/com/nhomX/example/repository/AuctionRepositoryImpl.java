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
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.MyAuctionStatus;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class AuctionRepositoryImpl implements AuctionRepository {

  // Formatter chuẩn để lưu/đọc thời gian nhất quán
  private static final DateTimeFormatter DB_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  // Khởi tạo và lưu một phiên đấu giá mới vào cơ sở dữ liệu với kết nối độc lập
  @Override
  public void save(Auction auction) {
    // Câu lệnh SQL với tham số ẩn (?) để tránh SQL Injection
    String sql =
        "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id, winner_id, approved_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    // Xin một kết nối tử Singleton:
    Connection conn = DatabaseConnection.getInstance().getConnection();
    // Sử dụng try-with-resources để tự động đóng Statement sau khi dùng xong
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      // Truyền dữ liệu số và chuỗi cơ bản
      pstmt.setString(1, auction.getId());
      pstmt.setLong(2, auction.getStartingPrice());
      pstmt.setLong(3, auction.getHighestBid());

      // Lưu start_time đúng định dạng: ép kiểu LocalDatetime sang String theo định dạng DB_Formatter
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
      System.out.println("✅ Đã lưu phiên đấu giá: " + auction.getId());
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lưu Auction: " + e.getMessage());
    }
  }

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
    String sql = "SELECT * " + "FROM auctions " + "WHERE status IN('OPEN', 'RUNNING') "
        + "AND start_time <= ? " + "AND end_time > ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      String now = LocalDateTime.now().format(DB_FORMATTER);

      pstmt.setString(1, now); // start_time <= now
      pstmt.setString(2, now); // end_time > now
      try (ResultSet rs = pstmt.executeQuery()) { // ← Query sau khi đã set tham số
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy ds phiên mở: " + e.getMessage());
    }
    return list;
  }

  @Override
  public List<Auction> findExpiredOpenAuctions() {
    List<Auction> list = new ArrayList<>();
    String now = LocalDateTime.now().format(DB_FORMATTER);
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

  // =========================================================================
  // BỔ SUNG HÀM LẤY DANH SÁCH "MY AUCTIONS" CỦA RIÊNG USER ĐANG ĐĂNG NHẬP
  // =========================================================================
  @Override
  public List<MyAuctionDTO> getMyAuctions(String userId) {
    List<MyAuctionDTO> list = new ArrayList<>();

    // SQL: Lấy thông tin phiên đấu giá + Giá cao nhất mà userId này từng đặt
    // Lưu ý: Tên bảng 'bids' và cột 'bidder_id', 'amount' có thể thay đổi tùy Database thực tế của
    // em.
    String sql = "SELECT a.*, MAX(b.amount) AS my_highest_bid " + "FROM auctions a "
        + "JOIN bids b ON a.id = b.auction_id " + "WHERE b.user_id = ? " + "GROUP BY a.id "
        + "ORDER BY a.end_time DESC";

    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
      System.err.println("❌ Lỗi khi lấy danh sách My Auctions: " + e.getMessage());
    }
    return list;
  }
  // HÀM PHỤ TRỢ: Map dòng dữ liệu từ DB sang Object

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
  // ---------------- TASK 1: CHIẾN LƯỢC LẤY DANH SÁCH ----------------

  @Override
  public List<Auction> getEndingSoonAuctions(int limit) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * FROM auctions " + "WHERE status IN ('OPEN', 'RUNNING') AND end_time > ? "
        + "ORDER BY end_time ASC LIMIT ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      // Dấu ? số 1 là thời gian hiện tại (Format chuẩn DB của nhóm bạn)
      pstmt.setString(1, java.time.LocalDateTime.now().format(DB_FORMATTER));
      pstmt.setInt(2, limit);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi getEndingSoonAuctions: " + e.getMessage());
    }
    return list;
  }

  @Override
  public List<Auction> getTrendingAuctions(int limit) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT a.* FROM auctions a " + "LEFT JOIN bids b ON a.id = b.auction_id "
        + "WHERE a.status = 'RUNNING' " + "GROUP BY a.id " + "ORDER BY COUNT(b.id) DESC LIMIT ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setInt(1, limit);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi getTrendingAuctions: " + e.getMessage());
    }
    return list;
  }

  // ---------------- TASK 2: VIẾT HÀM ĐẾM THỐNG KÊ ----------------

  @Override
  public int countActiveAuctions() {
    int count = 0;
    String sql = "SELECT COUNT(*) FROM auctions WHERE status IN ('OPEN', 'RUNNING')";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

      if (rs.next())
        count = rs.getInt(1);
    } catch (SQLException e) {
      System.err.println("❌ Lỗi countActiveAuctions: " + e.getMessage());
    }
    return count;
  }

  @Override
  public int countEndingSoonAuctions() {
    int count = 0;
    String sql = "SELECT COUNT(*) FROM auctions " + "WHERE status IN ('OPEN', 'RUNNING') "
        + "AND end_time > ? AND end_time <= ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      LocalDateTime now = java.time.LocalDateTime.now();
      LocalDateTime tomorrow = now.plusHours(24); // Sắp kết thúc trong 24h

      pstmt.setString(1, now.format(DB_FORMATTER));
      pstmt.setString(2, tomorrow.format(DB_FORMATTER));

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next())
          count = rs.getInt(1);
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi countEndingSoonAuctions: " + e.getMessage());
    }
    return count;
  }


  @Override
  public List<Auction> findAuctionsByStatus(AuctionStatus auctionStatus) {
    List<Auction> list = new ArrayList<>();
    String sql = "SELECT * FROM auctions WHERE status = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionStatus.name());
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi tìm Auction theo status: " + e.getMessage());
    }
    return list;
  }

  @Override
  public boolean updateAuctionStatus(Auction auction) {
    // Cập nhật cả status và end_time (phòng trường hợp gia hạn do Anti-sniping)
    String sql = "UPDATE auctions SET status = ?, end_time = ?, approved_by = ? WHERE id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auction.getStatus().name());
      pstmt.setString(2,
          auction.getEndTime() != null ? auction.getEndTime().format(DB_FORMATTER) : null);
      pstmt.setString(3, auction.getApprovedBy());
      pstmt.setString(4, auction.getId());

      int affectedRows = pstmt.executeUpdate();
      return affectedRows > 0;

    } catch (SQLException e) {
      System.err.println("❌ Lỗi cập nhật trạng thái phiên: " + e.getMessage());
      return false;
    }
  }

  @Override
  public List<Auction> findReadyToOpenAuctions() {
    List<Auction> list = new ArrayList<>();
    // Tìm các phiên đang CHỜ, nhưng thời gian hiện tại đã vượt qua giờ BẮT ĐẦU
    String sql = "SELECT * FROM auctions WHERE status = 'UP_COMING' AND start_time <= ?";

    // Sử dụng try-with-resources để tự động đóng kết nối, chuẩn Checkstyle
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      String now = java.time.LocalDateTime.now().format(DB_FORMATTER);
      pstmt.setString(1, now);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(mapRowToAuction(rs));
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi quét phiên UP_COMING: " + e.getMessage());
    }

    return list;
  }
}

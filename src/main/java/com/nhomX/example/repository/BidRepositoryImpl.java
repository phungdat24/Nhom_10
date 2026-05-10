package com.nhomX.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

public class BidRepositoryImpl implements BidRepository {
  // Kho chứa ổ khóa: Mỗi ID sản phẩm sẽ tương ứng với 1 ổ khóa riêng biệt
  private static final ConcurrentHashMap<String, ReentrantLock> auctionLocks =
      new ConcurrentHashMap<>();
  // Xóa bỏ biến Connection dùng chung gây rò rỉ và chết multi-thread

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
    String sql = "SELECT * FROM bids WHERE auction_id = ?";
    Connection conn = DatabaseConnection.getInstance().getConnection();
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {

        while (rs.next()) {
          BidTransaction bidTransaction = new BidTransaction();
          // Đọc dữ liệu từ DB và nhét vào đối tượng Bid
          // Thuộc tính cơ bản:
          bidTransaction.setId(rs.getString("id"));
          bidTransaction.setAmount(rs.getLong("amount"));

          // Xử lý ngày tháng (nếu có cột bid_time trong DB)
          String timeStr = rs.getString("bid_time");
          if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
              if (timeStr.contains("T")) {
                // TRƯỜNG HỢP 1: Chuẩn Java (VD: 2026-05-08T15:30:00)
                // Hàm parse mặc định của Java tự hiểu được chữ T này
                bidTransaction.setBidTime(java.time.LocalDateTime.parse(timeStr));
              } else {
                // TRƯỜNG HỢP 2: Chuẩn SQLite (VD: 2026-05-08 15:30:00)
                // Cần tự tạo một bộ dịch (Formatter) chỉ cho Java biết dấu cách nằm ở đâu
                DateTimeFormatter sqliteFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                bidTransaction.setBidTime(java.time.LocalDateTime.parse(timeStr, sqliteFormatter));
              }
            } catch (Exception e) {
              // Nuốt lỗi an toàn, không làm sập chương trình
              System.err.println("⚠️ Cảnh báo: Không thể parse thời gian từ DB: [" + timeStr + "]");
              // bidTransaction.getBidTime() lúc này sẽ tự động hiểu là null
            }
          }

          // ÁNH XẠ KHÓA NGOẠI THÀNH OBJECT CƠ BẢN
          // Thay vì đi tìm nguyên cả User trong DB (rất chậm), ta tạo một User "vỏ rỗng" chứa ID
          RegularUser bidder = new RegularUser();
          bidder.setId(rs.getString("user_id"));
          bidTransaction.setBidder(bidder); // Gán Object vào Bid

          // Tương tự với Phiên đấu giá
          Auction auction = new Auction();
          auction.setId(rs.getString("auction_id"));
          bidTransaction.setAuction(auction);

          // Cất vào danh sách
          listBidTransactions.add(bidTransaction);
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
          BidTransaction bidTransaction = new BidTransaction();

          bidTransaction.setId(rs.getString("id"));
          bidTransaction.setAmount(rs.getLong("amount"));

          // Ánh xạ thời gian (SQLite thường lưu dạng chuỗi ISO-8601)
          String timeStr = rs.getString("bid_time");
          if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
              if (timeStr.contains("T")) {
                // TRƯỜNG HỢP 1: Chuẩn Java (VD: 2026-05-08T15:30:00)
                // Hàm parse mặc định của Java tự hiểu được chữ T này
                bidTransaction.setBidTime(java.time.LocalDateTime.parse(timeStr));
              } else {
                // TRƯỜNG HỢP 2: Chuẩn SQLite (VD: 2026-05-08 15:30:00)
                // Cần tự tạo một bộ dịch (Formatter) chỉ cho Java biết dấu cách nằm ở đâu
                DateTimeFormatter sqliteFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                bidTransaction.setBidTime(java.time.LocalDateTime.parse(timeStr, sqliteFormatter));
              }
            } catch (Exception e) {
              // Nuốt lỗi an toàn, không làm sập chương trình
              System.err.println("⚠️ Cảnh báo: Không thể parse thời gian từ DB: [" + timeStr + "]");
              // bidTransaction.getBidTime() lúc này sẽ tự động hiểu là null
            }
          }

          // 2. Tạo Vỏ rỗng cho Người đặt (ĐỂ TÌM NGƯỜI THẮNG)
          RegularUser bidder = new RegularUser();
          bidder.setId(rs.getString("user_id"));
          bidTransaction.setBidder(bidder);

          // 3. Tạo Vỏ rỗng cho Phiên đấu giá
          Auction auctionObj = new Auction();
          auctionObj.setId(rs.getString("auction_id"));
          bidTransaction.setAuction(auctionObj);
          return bidTransaction;
        }
      }
    } catch (SQLException e) {
      System.err.println("❌ Lỗi khi lấy giá cao nhất: " + e.getMessage());
    }
    return null;
  }

  @Override
  public boolean placeBidTransaction(String userId, String auctionId, long bidAmount,
      String bidId) {

    Connection conn = DatabaseConnection.getInstance().getConnection();
    // Bắt lỗi rollback:
    try {

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
      String sqlBid =
              "INSERT INTO bids (id, amount, bid_time, user_id, auction_id) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement pstmt2 = conn.prepareStatement(sqlBid)) {

        pstmt2.setString(1, bidId);
        pstmt2.setLong(2, bidAmount);
        // Ép Java tạo thời gian chuẩn có chữ T để lưu xuống DB
        pstmt2.setString(3, java.time.LocalDateTime.now().toString());
        pstmt2.setString(4, userId);
        pstmt2.setString(5, auctionId);

        pstmt2.executeUpdate();
      }

      // Lệnh 3: Cập nhật giá hiện tại của sản phẩm
      String sqlAuction = "UPDATE auctions SET highest_bid = ? WHERE id = ?";
      try (PreparedStatement pstmt3 = conn.prepareStatement(sqlAuction)) {
        pstmt3.setLong(1, bidAmount);
        pstmt3.setString(2, auctionId);
        pstmt3.executeUpdate();
      }

      // BƯỚC 4: Commit giao dịch
      conn.commit();
      System.out.println("✅ Giao dịch thành công! Đã chốt sổ dữ liệu.");
      return true;

    } catch (SQLException e) {
      // BƯỚC 5: Mở khối catch - Có lỗi xảy ra, tiến hành quay xe (Rollback)
      System.err.println("❌ Lỗi Giao dịch! Đang hoàn tác (Rollback)... Lý do: " + e.getMessage());
      try {
        conn.rollback();
        System.out.println("🔄 Đã hoàn tác an toàn. Không ai bị mất tiền oan.");
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi nghiêm trọng khi Rollback: " + ex.getMessage());
      }
      return false;
    } finally {
      // Khôi phục lại chốt an toàn:
      try {
        conn.setAutoCommit(true);
      } catch (SQLException ex) {
        System.err.println("❌ Lỗi khi khôi phục commit " + ex.getMessage());
      }
    }
  }
}

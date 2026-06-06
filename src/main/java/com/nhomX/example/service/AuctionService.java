package com.nhomX.example.service;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.Items;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.utils.DatabaseConnection;

public class AuctionService {
  private static final Logger logger = LoggerFactory.getLogger(AuctionService.class);
  private final ItemRepositoryImpl itemRepo = new ItemRepositoryImpl();
  private final AuctionRepositoryImpl auctionRepo = new AuctionRepositoryImpl();

  public boolean createAuctionListing(Items item, Auction auction) {
    Connection conn = null;
    try {
      conn = DatabaseConnection.getInstance().getConnection();
      // Tắt auto-commit bắt đầu Transaction
      conn.setAutoCommit(false);

      itemRepo.save(item, conn);
      auctionRepo.save(auction, conn);

      conn.commit();
      logger.info("SERVICE: Đã lưu thành công sản phẩm và phiên đấu giá vào DB");
      return true;

    } catch (Exception e) {
      logger.error("SERVICE LỖI: Đang rollback toàn bộ dữ liệu", e);
      try {
        if (conn != null) {
          conn.rollback();
        }
      } catch (SQLException ex) {
        logger.error("Lỗi rollback transaction", ex);
      }
      return false;
    } finally {
      try {
        // Luôn trả lại Auto-commit
        if (conn != null) {
          conn.setAutoCommit(true);
          conn.close();
        }
      } catch (SQLException e) {
        logger.error("Lỗi khi khôi phục AutoCommit hoặc đóng Connection", e);
      }
    }
  }
}

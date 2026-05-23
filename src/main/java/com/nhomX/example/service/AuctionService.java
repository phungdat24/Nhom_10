package com.nhomX.example.service;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.Items;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.utils.DatabaseConnection;
import java.sql.Connection;
import java.sql.SQLException;

public class AuctionService {
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
      System.out.println("SERVICE: Da luu thanh cong san pham va phien dau gia vao DB.");
      return true;

    } catch (Exception e) {
      System.err.println("SERVICE LOI: Dang rollback toan bo du lieu... " + e.getMessage());
      try {
        if (conn != null) {
          conn.rollback();
        }
      } catch (SQLException ex) {
        ex.printStackTrace();
      }
      return false;
    } finally {
      try {
        //Luôn trả lại Auto-commit
        if (conn != null) {
          conn.setAutoCommit(true);
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }
}

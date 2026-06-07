package com.nhomX.example.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;
import com.nhomX.example.testsupport.DatabaseBackedTest;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử service tạo phiên đấu giá với transaction.
 * Đây là test scaffolding mô phỏng DB tạm để kiểm tra commit và rollback.
 */
class AuctionServiceTest extends DatabaseBackedTest {
  private final UserRepository userRepository = new UserRepositoryImpl();
  private final ItemRepository itemRepository = new ItemRepositoryImpl();
  private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();
  private final AuctionService service = new AuctionService();

  @Test
  void createAuctionListingPersistsItemAndAuctionAtomicallyOnSuccess() {
    // Oracle commit: item và auction phải cùng được lưu khi dữ liệu hợp lệ.
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    Auction auction = auction("auction", item, AuctionStatus.OPEN, 100);

    assertTrue(service.createAuctionListing(item, auction));

    assertNotNull(itemRepository.findById("item"));
    assertNotNull(auctionRepository.findById("auction"));
  }

  @Test
  void createAuctionListingRollsBackItemWhenAuctionInsertFails() {
    // Oracle rollback: nếu lưu auction lỗi FK, item đã insert trước đó cũng phải bị rollback.
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    Auction auction = auction("auction", item, AuctionStatus.OPEN, 100);
    RegularUser missingWinner = bidder("missing-winner", 0);
    auction.setWinner(missingWinner);

    assertFalse(service.createAuctionListing(item, auction));

    assertNull(itemRepository.findById("item"));
    assertNull(auctionRepository.findById("auction"));
  }
}

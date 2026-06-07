package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.testsupport.DatabaseBackedTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử các truy vấn cơ bản của sản phẩm và phiên đấu giá.
 * Các test giữ lại tập trung vào trạng thái phiên, lọc phiên đang mở và tất toán tiền.
 */
class ItemAndAuctionRepositoryTest extends DatabaseBackedTest {
  private final UserRepository userRepository = new UserRepositoryImpl();
  private final ItemRepository itemRepository = new ItemRepositoryImpl();
  private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();

  @Test
  void itemSaveFindAndListPreserveImagesSellerAndCategory() {
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    item.addImage(new ItemImage("img1", "one.png", item.getId()));
    item.addImage(new ItemImage("img2", "two.png", item.getId()));

    itemRepository.save(item);

    Items found = itemRepository.findById("item");
    assertEquals("Item item", found.getTitle());
    assertEquals("seller", found.getSeller().getId());
    assertEquals(2, found.getImages().size());
    assertEquals(1, itemRepository.findAll().size());
    assertEquals(1, itemRepository.findByCategory("GENERALITEM").size());
    assertEquals(1, itemRepository.findBySellerId("seller").size());
  }

  @Test
  void itemUpdateReplacesFieldsAndImages() {
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    item.addImage(new ItemImage("img1", "one.png", item.getId()));
    itemRepository.save(item);

    item.setTitle("Updated");
    item.setDescription("Updated description");
    item.setImages(List.of(new ItemImage("img2", "two.png", item.getId())));
    itemRepository.update(item);

    Items updated = itemRepository.findById("item");
    assertEquals("Updated", updated.getTitle());
    assertEquals("Updated description", updated.getDescription());
    assertEquals(List.of("two.png"), updated.getImages().stream().map(ItemImage::getImagePath).toList());
  }

  @Test
  void itemDeleteRemovesItemAndImages() {
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    item.addImage(new ItemImage("img1", "one.png", item.getId()));
    itemRepository.save(item);

    itemRepository.delete("item");

    assertNull(itemRepository.findById("item"));
    assertTrue(itemRepository.findAll().isEmpty());
  }

  @Test
  void auctionSaveFindAndUpdatesRoundTrip() {
    // Luồng cơ bản: lưu phiên, cập nhật người thắng/giá cao nhất, cập nhật giờ kết thúc và trạng thái.
    RegularUser seller = seller("seller");
    RegularUser winner = bidder("winner", 1_000);
    saveUsers(userRepository, seller, winner, admin("admin"));
    GeneralItem item = item("item", seller);
    itemRepository.save(item);
    Auction auction = auction("auction", item, AuctionStatus.OPEN, 100);
    auction.setApprovedBy("admin");
    auctionRepository.save(auction);

    auctionRepository.updateHighestBidAndWinner("auction", 250, "winner");
    LocalDateTime newEndTime = LocalDateTime.now().plusHours(2).withNano(0);
    auctionRepository.updateEndTime("auction", newEndTime);
    auctionRepository.updateStatus("auction", AuctionStatus.FINISHED);

    Auction found = auctionRepository.findById("auction");
    assertEquals(250, found.getHighestBid());
    assertEquals("winner", found.getWinner().getId());
    assertEquals(AuctionStatus.FINISHED, found.getStatus());
    assertEquals(newEndTime, found.getEndTime());
    assertEquals("admin", found.getApprovedBy());
    assertNotNull(found.getItem());
  }

  @Test
  void activeExpiredReadyAndStatusQueriesFilterByBusinessRules() {
    // EP theo trạng thái và thời gian: active, expired, ready-to-open và future-upcoming.
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    itemRepository.save(item);
    LocalDateTime now = LocalDateTime.now();
    Auction active = datedAuction("active", item, AuctionStatus.OPEN, now.minusMinutes(1), now.plusMinutes(10), 100);
    Auction expired = datedAuction("expired", item, AuctionStatus.RUNNING, now.minusMinutes(10), now.minusSeconds(1), 100);
    Auction ready = datedAuction("ready", item, AuctionStatus.UP_COMING, now.minusMinutes(1), now.plusMinutes(10), 100);
    Auction future = datedAuction("future", item, AuctionStatus.UP_COMING, now.plusMinutes(5), now.plusMinutes(10), 100);
    saveAuctions(auctionRepository, active, expired, ready, future);

    assertEquals(List.of("active"), auctionRepository.findAllActiveAuctions().stream().map(Auction::getId).toList());
    assertEquals(List.of("expired"), auctionRepository.findExpiredOpenAuctions().stream().map(Auction::getId).toList());
    assertTrue(auctionRepository.findReadyToOpenAuctions().stream().map(Auction::getId).toList().contains("ready"));
    assertFalse(auctionRepository.findReadyToOpenAuctions().stream().map(Auction::getId).toList().contains("future"));
    assertEquals(2, auctionRepository.findAuctionsByStatus(AuctionStatus.UP_COMING).size());
  }

  @Test
  void sellerLiveAndCountQueriesReturnExpectedAuctions() {
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    itemRepository.save(item);
    Auction open = auction("open", item, AuctionStatus.OPEN, 100);
    Auction running = auction("running", item, AuctionStatus.RUNNING, 100);
    Auction canceled = auction("canceled", item, AuctionStatus.CANCELED, 100);
    saveAuctions(auctionRepository, open, running, canceled);

    assertEquals(3, auctionRepository.findBySellerId("seller").size());
    assertEquals(2, auctionRepository.countActiveAuctions());
    assertEquals(2, auctionRepository.findLiveAuctions().size());
  }

  @Test
  void endingSoonQueriesUseTimeWindowAndLimit() {
    RegularUser seller = seller("seller");
    saveUsers(userRepository, seller);
    GeneralItem item = item("item", seller);
    itemRepository.save(item);
    LocalDateTime now = LocalDateTime.now();
    Auction soon = datedAuction("soon", item, AuctionStatus.OPEN, now.minusMinutes(1), now.plusMinutes(5), 100);
    Auction later = datedAuction("later", item, AuctionStatus.OPEN, now.minusMinutes(1), now.plusHours(2), 100);
    Auction tomorrow = datedAuction("tomorrow", item, AuctionStatus.OPEN, now.minusMinutes(1), now.plusHours(25), 100);
    saveAuctions(auctionRepository, soon, later, tomorrow);

    assertEquals(List.of("soon", "later"), auctionRepository.getEndingSoonAuctions(2).stream().map(Auction::getId).toList());
    assertEquals(2, auctionRepository.countEndingSoonAuctions());
  }

  @Test
  void settleAuctionPaymentPaysSellerOrCancelsWithoutWinner() {
    // Oracle tài chính: phiên có winner trả tiền cho seller; phiên không winner chuyển CANCELED.
    RegularUser seller = seller("seller");
    RegularUser winner = bidder("winner", 1_000);
    saveUsers(userRepository, seller, winner);
    GeneralItem soldItem = item("sold-item", seller);
    GeneralItem unsoldItem = item("unsold-item", seller);
    saveItems(itemRepository, soldItem, unsoldItem);
    Auction sold = auction("sold", soldItem, AuctionStatus.FINISHED, 100);
    sold.setHighestBid(300);
    sold.setWinner(winner);
    Auction unsold = auction("unsold", unsoldItem, AuctionStatus.FINISHED, 100);
    saveAuctions(auctionRepository, sold, unsold);

    assertTrue(auctionRepository.settleAuctionPayment("sold"));
    assertEquals(AuctionStatus.PAID, auctionRepository.findById("sold").getStatus());
    assertEquals(300, userRepository.findById("seller").getBalance());

    assertTrue(auctionRepository.settleAuctionPayment("unsold"));
    assertEquals(AuctionStatus.CANCELED, auctionRepository.findById("unsold").getStatus());
    assertFalse(auctionRepository.settleAuctionPayment("missing"));
  }
}

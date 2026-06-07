package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.AutoBidConfig;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.testsupport.DatabaseBackedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử lưu và truy vấn cấu hình đấu giá tự động.
 * Các test dùng scaffolding SQLite tạm để kiểm soát dữ liệu user, auction và trạng thái active.
 */
class AutoBidRepositoryImplTest extends DatabaseBackedTest {
  private final UserRepository userRepository = new UserRepositoryImpl();
  private final ItemRepository itemRepository = new ItemRepositoryImpl();
  private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();
  private final AutoBidRepositoryImpl repository = new AutoBidRepositoryImpl();

  private RegularUser seller;
  private RegularUser firstBidder;
  private RegularUser secondBidder;
  private Auction auction;

  @BeforeEach
  void seedAuction() {
    // Điều kiện trước: một phiên OPEN và hai bidder để kiểm tra sắp xếp, loại trừ và vô hiệu hóa.
    seller = seller("seller");
    firstBidder = bidder("first", 1_000);
    secondBidder = bidder("second", 1_000);
    saveUsers(userRepository, seller, firstBidder, secondBidder);
    GeneralItem item = item("item", seller);
    itemRepository.save(item);
    auction = auction("auction", item, AuctionStatus.OPEN, 100);
    auctionRepository.save(auction);
  }

  @Test
  void saveInsertsAndFindsAutoBidConfig() {
    // Input hợp lệ: maxLimit > current price và increment > 0.
    // Oracle: repository lưu được config và đọc lại đúng user, auction, max/step.
    AutoBidConfig config = new AutoBidConfig("config", 500, 50, firstBidder, auction);

    assertTrue(repository.save(config));

    AutoBidConfig found = repository.findByUserAndAuction("first", "auction");
    assertNotNull(found);
    assertEquals(500, found.getMaxLimit());
    assertEquals(50, found.getIncrement());
    assertEquals("first", found.getBidder().getId());
    assertEquals("auction", found.getAuction().getId());
  }

  @Test
  void saveUpdatesExistingUserAuctionConfig() {
    // Phân hoạch trùng khóa (user, auction): lần lưu thứ hai phải update thay vì tạo bản ghi mới.
    assertTrue(repository.save(new AutoBidConfig("config-1", 500, 50, firstBidder, auction)));
    assertTrue(repository.save(new AutoBidConfig("config-2", 700, 25, firstBidder, auction)));

    AutoBidConfig found = repository.findByUserAndAuction("first", "auction");
    assertEquals(700, found.getMaxLimit());
    assertEquals(25, found.getIncrement());
  }

  @Test
  void activeConfigsAreSortedByMaxPriceAndCanExcludeUser() {
    // Oracle của auto-bid: cấu hình có max cao hơn được ưu tiên xử lý trước,
    // và bidder vừa thắng có thể bị loại khỏi danh sách kích hoạt.
    assertTrue(repository.save(new AutoBidConfig("first-config", 500, 50, firstBidder, auction)));
    assertTrue(repository.save(new AutoBidConfig("second-config", 800, 50, secondBidder, auction)));

    assertEquals(
        java.util.List.of("second", "first"),
        repository.findByAuctionId("auction").stream().map(config -> config.getBidder().getId()).toList());
    assertEquals(
        java.util.List.of("second"),
        repository.findActiveByAuctionId("auction", "first").stream().map(config -> config.getBidder().getId()).toList());
  }

  @Test
  void deactivateAndDeleteRemoveConfigFromActiveQueries() {
    // Kiểm tra vòng đời config: deactivate chỉ tắt active, delete mới xóa hẳn bản ghi.
    assertTrue(repository.save(new AutoBidConfig("config", 500, 50, firstBidder, auction)));
    AutoBidConfig saved = repository.findByUserAndAuction("first", "auction");

    repository.deactivate(saved.getId());

    assertTrue(repository.findByAuctionId("auction").isEmpty());
    assertNotNull(repository.findByUserAndAuction("first", "auction"));

    repository.delete(saved.getId());
    assertNull(repository.findByUserAndAuction("first", "auction"));
  }

  @Test
  void deactivateByUserAndAuctionStopsActiveConfig() {
    assertTrue(repository.save(new AutoBidConfig("config", 500, 50, firstBidder, auction)));

    repository.deactivateByUserAndAuction("first", "auction");

    assertTrue(repository.findByAuctionId("auction").isEmpty());
  }

  @Test
  void invalidConfigIsRejectedBeforeSql() {
    // Phân hoạch lỗi đầu vào: null hoặc thiếu user/auction phải bị chặn trước khi đụng database.
    assertFalse(repository.save(null));
    assertFalse(repository.save(new AutoBidConfig()));
  }
}

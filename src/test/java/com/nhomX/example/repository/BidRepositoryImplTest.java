package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.exception.InvalidBidException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.testsupport.DatabaseBackedTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử giao dịch đặt giá theo tài liệu 8.1:
 * dùng phân hoạch tương đương cho bid hợp lệ/không hợp lệ, và giá trị biên quanh highestBid.
 */
class BidRepositoryImplTest extends DatabaseBackedTest {
  private final UserRepository userRepository = new UserRepositoryImpl();
  private final ItemRepository itemRepository = new ItemRepositoryImpl();
  private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();
  private final BidRepository bidRepository = new BidRepositoryImpl();

  private RegularUser seller;
  private RegularUser firstBidder;
  private RegularUser secondBidder;
  private GeneralItem item;

  @BeforeEach
  void seedUsersAndItem() {
    // Test harness: mỗi test chạy trên SQLite tạm, có 1 seller và 2 bidder độc lập.
    seller = regularUser("seller", "seller@example.com", 0, Role.SELLER);
    firstBidder = regularUser("first", "first@example.com", 1_000, Role.BIDDER);
    secondBidder = regularUser("second", "second@example.com", 1_000, Role.BIDDER);
    saveUsers(userRepository, seller, firstBidder, secondBidder);
    item = item("item", seller);
    itemRepository.save(item);
  }

  @Test
  void validBidUpdatesAuctionBalanceAndHistory() {
    // Input: bidder "first" đặt 200, lớn hơn giá hiện tại 100.
    // Điều kiện trước: phiên OPEN, bidder đủ số dư.
    // Oracle: phiên chuyển RUNNING, winner đổi, tiền bị trừ và lịch sử bid được ghi.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertTrue(bidRepository.executeBidTransaction("first", auction.getId(), 200, "bid-1"));

    Auction updated = auctionRepository.findById(auction.getId());
    assertEquals(200, updated.getHighestBid());
    assertEquals("first", updated.getWinner().getId());
    assertEquals(AuctionStatus.RUNNING, updated.getStatus());
    assertEquals(800, userRepository.findById("first").getBalance());
    assertEquals(List.of(200L), bidRepository.getBidsByAuctionId(auction.getId()).stream().map(BidTransaction::getAmount).toList());
    assertEquals(200, bidRepository.getHighestBid(auction.getId()).getAmount());
  }

  @Test
  void lowerOrEqualBidIsRejected() {
    // BVA quanh biên highestBid = 100: kiểm tra min- (99) và min (100) đều bị từ chối.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("first", auction.getId(), 100, "bid-low"));
    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("first", auction.getId(), 99, "bid-lower"));
    assertEquals(1_000, userRepository.findById("first").getBalance());
  }

  @Test
  void sellerCannotBidOnOwnAuction() {
    // Phân hoạch lỗi nghiệp vụ: user là seller của item nên không được tự đẩy giá.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("seller", auction.getId(), 200, "bid-seller"));
  }

  @Test
  void upcomingAuctionIsRejectedAsInvalidBid() {
    // Điều kiện trước: phiên chưa đến giờ mở bán, dù bid có giá hợp lệ vẫn không được nhận.
    Auction auction = saveAuction("auction", AuctionStatus.UP_COMING, LocalDateTime.now().plusMinutes(30));

    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("first", auction.getId(), 200, "bid-upcoming"));
  }

  @Test
  void closedStatusesAndExpiredAuctionsRejectBids() {
    // Phân hoạch trạng thái đóng: CANCELED và OPEN nhưng quá hạn đều phải chặn bid.
    Auction canceled = saveAuction("canceled", AuctionStatus.CANCELED, LocalDateTime.now().plusMinutes(30));
    Auction expired = saveAuction("expired", AuctionStatus.OPEN, LocalDateTime.now().minusSeconds(1));

    assertThrows(AuctionClosedException.class, () -> bidRepository.executeBidTransaction("first", canceled.getId(), 200, "bid-canceled"));
    assertThrows(AuctionClosedException.class, () -> bidRepository.executeBidTransaction("first", expired.getId(), 200, "bid-expired"));
  }

  @Test
  void missingAuctionOrUserIsRejected() {
    // Phân hoạch dữ liệu thiếu: thiếu auction hoặc thiếu user đều không được tạo giao dịch.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("first", "missing", 200, "bid-missing-auction"));
    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("missing", auction.getId(), 200, "bid-missing-user"));
  }

  @Test
  void insufficientBalanceRollsBackTransaction() {
    // Oracle quan trọng: khi số dư không đủ, toàn bộ transaction phải rollback.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertThrows(InvalidBidException.class, () -> bidRepository.executeBidTransaction("first", auction.getId(), 1_001, "bid-rich"));

    assertEquals(1_000, userRepository.findById("first").getBalance());
    assertEquals(100, auctionRepository.findById(auction.getId()).getHighestBid());
    assertTrue(bidRepository.getBidsByAuctionId(auction.getId()).isEmpty());
  }

  @Test
  void outbidRefundsOldWinnerAndDeductsNewWinner() {
    // Luồng đấu giá cơ bản: người mới trả cao hơn phải được dẫn đầu, người cũ được hoàn tiền.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertTrue(bidRepository.executeBidTransaction("first", auction.getId(), 200, "bid-1"));
    assertTrue(bidRepository.executeBidTransaction("second", auction.getId(), 300, "bid-2"));

    assertEquals(1_000, userRepository.findById("first").getBalance());
    assertEquals(700, userRepository.findById("second").getBalance());
    assertEquals("second", auctionRepository.findById(auction.getId()).getWinner().getId());
  }

  @Test
  void selfOutbidOnlyDeductsDifference() {
    // Trường hợp cùng bidder nâng giá: chỉ trừ phần chênh lệch, không trừ lại toàn bộ bid mới.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));

    assertTrue(bidRepository.executeBidTransaction("first", auction.getId(), 200, "bid-1"));
    assertTrue(bidRepository.executeBidTransaction("first", auction.getId(), 250, "bid-2"));

    assertEquals(750, userRepository.findById("first").getBalance());
    assertEquals(2, bidRepository.getBidsByAuctionId(auction.getId()).size());
  }

  @Test
  void antiSnipeExtendsAuctionNearEnd() {
    // Best practice trong PDF: với logic thời gian, không kiểm tra giây tuyệt đối,
    // chỉ kiểm tra thuộc tính mong muốn là endTime được kéo dài đủ xa.
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(1));

    assertTrue(bidRepository.executeBidTransaction("first", auction.getId(), 200, "bid-1"));

    assertTrue(auctionRepository.findById(auction.getId()).getEndTime().isAfter(LocalDateTime.now().plusMinutes(4)));
  }

  @Test
  void addBidAndGetHighestBidWorkForManualHistory() {
    Auction auction = saveAuction("auction", AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(30));
    BidTransaction first = new BidTransaction("manual-1", LocalDateTime.now().minusSeconds(2), 150, firstBidder, auction);
    BidTransaction second = new BidTransaction("manual-2", LocalDateTime.now().minusSeconds(1), 250, secondBidder, auction);

    bidRepository.addBid(first);
    bidRepository.addBid(second);

    assertEquals(List.of(150L, 250L), bidRepository.getBidsByAuctionId(auction.getId()).stream().map(BidTransaction::getAmount).toList());
    assertEquals(250, bidRepository.getHighestBid(auction.getId()).getAmount());
  }

  @Test
  void saveAutoBidConfigStubReturnsFalse() {
    assertFalse(bidRepository.saveAutoBidConfig("first", "auction", 500, 50));
  }

  private Auction saveAuction(String id, AuctionStatus status, LocalDateTime endTime) {
    Auction auction = datedAuction(
        id,
        item,
        status,
        LocalDateTime.now().minusMinutes(1),
        endTime.withNano(0),
        100);
    auctionRepository.save(auction);
    return auction;
  }
}

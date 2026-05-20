package com.nhomX.example.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.BidRepositoryImpl;
import com.nhomX.example.repository.DatabaseBackedTest;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;
import com.nhomX.example.utils.SecurityUtils;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionFlowTest extends DatabaseBackedTest {
  @Test
  void fullAuctionFlow() {
    UserRepository userRepository = new UserRepositoryImpl();
    ItemRepository itemRepository = new ItemRepositoryImpl();
    AuctionRepository auctionRepository = new AuctionRepositoryImpl();
    BidRepository bidRepository = new BidRepositoryImpl();

    // Arrange: tạo dữ liệu giống luồng app: user, item, phiên đấu giá đang mở.
    RegularUser seller = regularUser("seller-1", "seller@example.com", 0, Role.SELLER);
    RegularUser bidder = regularUser("bidder-1", "bidder@example.com", 1_000, Role.BIDDER);
    assertTrue(userRepository.register(seller));
    assertTrue(userRepository.register(bidder));

    // Login kiểm tra đúng tài khoản bidder được lấy ra từ database.
    User loggedIn = userRepository.login(
        "bidder@example.com", SecurityUtils.hashPassword("password"));
    assertNotNull(loggedIn);
    assertEquals("bidder-1", loggedIn.getId());

    Items item = new GeneralItem("item-1", "Vintage Clock", "Working antique clock", seller);
    itemRepository.save(item);

    Auction auction =
        new Auction("auction-1", item, LocalDateTime.now().plusMinutes(20), 100);
    auction.setStartTime(LocalDateTime.now().minusMinutes(1));
    auction.setStatus(AuctionStatus.OPEN);
    auction.setApprovedBy("admin-1");
    auctionRepository.save(auction);

    // Act 1: người dùng xem danh sách phiên đang mở.
    List<Auction> activeAuctions = auctionRepository.findAllActiveAuctions();

    // Assert 1: phiên vừa tạo xuất hiện cùng thông tin item.
    assertEquals(1, activeAuctions.size());
    assertEquals("Vintage Clock", activeAuctions.getFirst().getItem().getTitle());

    // Act 2: bidder đặt giá hợp lệ.
    assertTrue(bidRepository.executeBidTransaction(
        bidder.getId(), auction.getId(), 150, "bid-1"));

    // Assert 2: giá, winner, status và số dư đều được cập nhật nhất quán.
    Auction afterBid = auctionRepository.findById(auction.getId());
    assertEquals(150, afterBid.getHighestBid());
    assertEquals("bidder-1", afterBid.getWinner().getId());
    assertEquals(AuctionStatus.RUNNING, afterBid.getStatus());
    assertEquals(850, userRepository.findById("bidder-1").getBalance());

    List<BidTransaction> history = bidRepository.getBidsByAuctionId(auction.getId());
    assertEquals(1, history.size());
    assertEquals(150, history.getFirst().getAmount());

    // Act 3: giả lập hết giờ và đóng phiên.
    afterBid.setEndTime(LocalDateTime.now().minusSeconds(1));
    afterBid.closeAuction();
    assertTrue(auctionRepository.updateAuctionStatus(afterBid));

    // Assert 3: phiên đã FINISHED và không nhận bid mới.
    Auction closed = auctionRepository.findById(auction.getId());
    assertEquals(AuctionStatus.FINISHED, closed.getStatus());
    assertEquals("bidder-1", closed.getWinner().getId());

    assertThrows(AuctionClosedException.class, () -> bidRepository.executeBidTransaction(
        bidder.getId(), auction.getId(), 200, "bid-after-close"));
    assertEquals(850, userRepository.findById("bidder-1").getBalance());
  }

  @Test
  void expiredQueryIgnoresFuture() {
    UserRepository userRepository = new UserRepositoryImpl();
    ItemRepository itemRepository = new ItemRepositoryImpl();
    AuctionRepository auctionRepository = new AuctionRepositoryImpl();

    // Arrange: một phiên đã hết giờ và một phiên vẫn còn thời gian.
    RegularUser seller = regularUser("seller-2", "seller2@example.com", 0, Role.SELLER);
    assertTrue(userRepository.register(seller));

    Items item = new GeneralItem("item-2", "Camera", "Film camera", seller);
    itemRepository.save(item);

    Auction pastAuction =
        new Auction("past-auction", item, LocalDateTime.now().minusSeconds(5), 100);
    pastAuction.setStartTime(LocalDateTime.now().minusMinutes(10));
    pastAuction.setStatus(AuctionStatus.OPEN);
    pastAuction.setApprovedBy("admin-1");
    auctionRepository.save(pastAuction);

    Auction futureAuction =
        new Auction("future-auction", item, LocalDateTime.now().plusHours(1), 100);
    futureAuction.setStartTime(LocalDateTime.now().minusMinutes(10));
    futureAuction.setStatus(AuctionStatus.OPEN);
    futureAuction.setApprovedBy("admin-1");
    auctionRepository.save(futureAuction);

    // Act: query danh sách phiên cần scheduler đóng.
    List<String> expiredAuctionIds = auctionRepository.findExpiredOpenAuctions().stream()
        .map(Auction::getId)
        .toList();

    // Assert: chỉ phiên quá hạn được trả về.
    assertTrue(expiredAuctionIds.contains("past-auction"));
    assertFalse(expiredAuctionIds.contains("future-auction"));
  }
}

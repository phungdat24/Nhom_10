package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConcurrentBidTest extends DatabaseBackedTest {
  @Test
  void lastSecondRaceKeepsHighestBid() throws Exception {
    UserRepository userRepository = new UserRepositoryImpl();
    ItemRepository itemRepository = new ItemRepositoryImpl();
    AuctionRepository auctionRepository = new AuctionRepositoryImpl();
    BidRepository bidRepository = new BidRepositoryImpl();

    // Arrange: tạo seller, hai bidder và một phiên sắp hết giờ.
    RegularUser seller = regularUser("seller-3", "seller3@example.com", 0, Role.SELLER);
    RegularUser lowBidder = regularUser("bidder-low", "low@example.com", 1_000, Role.BIDDER);
    RegularUser highBidder = regularUser("bidder-high", "high@example.com", 1_000, Role.BIDDER);
    assertTrue(userRepository.register(seller));
    assertTrue(userRepository.register(lowBidder));
    assertTrue(userRepository.register(highBidder));

    Items item = new GeneralItem("item-3", "Headphones", "Studio headphones", seller);
    itemRepository.save(item);

    LocalDateTime originalEndTime = LocalDateTime.now().plusSeconds(2).withNano(0);
    LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
    Auction auction = new Auction("auction-concurrent", item, startTime, originalEndTime, 100L);
    auction.setStatus(AuctionStatus.OPEN);
    auction.setApprovedBy("admin-1");
    auctionRepository.save(auction);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      // Act: ép hai thread cùng bắn bid ở gần như cùng thời điểm.
      Future<BidOutcome> lowBid = executor.submit(concurrentBid(
          bidRepository, ready, start, lowBidder.getId(), auction.getId(), 200, "bid-low"));
      Future<BidOutcome> highBid = executor.submit(concurrentBid(
          bidRepository, ready, start, highBidder.getId(), auction.getId(), 300, "bid-high"));

      assertTrue(ready.await(2, TimeUnit.SECONDS));
      start.countDown();

      BidOutcome lowOutcome = lowBid.get(5, TimeUnit.SECONDS);
      BidOutcome highOutcome = highBid.get(5, TimeUnit.SECONDS);

      assertTrue(highOutcome.success(), "The highest bid must be accepted");
      assertTrue(lowOutcome.success() || lowOutcome.error() instanceof RuntimeException);
    } finally {
      executor.shutdownNow();
    }

    // Assert: người trả giá cao nhất thắng, số dư/refund không bị sai lệch.
    Auction afterRace = auctionRepository.findById(auction.getId());
    assertEquals(300, afterRace.getHighestBid());
    assertNotNull(afterRace.getWinner());
    assertEquals(highBidder.getId(), afterRace.getWinner().getId());
    assertTrue(afterRace.getEndTime().isAfter(originalEndTime));

    assertEquals(1_000, userRepository.findById(lowBidder.getId()).getBalance());
    assertEquals(700, userRepository.findById(highBidder.getId()).getBalance());

    List<Long> amounts = bidRepository.getBidsByAuctionId(auction.getId()).stream()
        .map(bid -> bid.getAmount())
        .toList();
    assertTrue(amounts.contains(300L));
    assertFalse(amounts.stream().anyMatch(amount -> amount <= 100));
  }

  @Test
  void equalRaceHasOneWinner() throws Exception {
    UserRepository userRepository = new UserRepositoryImpl();
    ItemRepository itemRepository = new ItemRepositoryImpl();
    AuctionRepository auctionRepository = new AuctionRepositoryImpl();
    BidRepository bidRepository = new BidRepositoryImpl();

    // Arrange: hai bidder cùng muốn đặt đúng một mức giá.
    RegularUser seller = regularUser("seller-4", "seller4@example.com", 0, Role.SELLER);
    RegularUser firstBidder = regularUser("bidder-a", "a@example.com", 1_000, Role.BIDDER);
    RegularUser secondBidder = regularUser("bidder-b", "b@example.com", 1_000, Role.BIDDER);
    assertTrue(userRepository.register(seller));
    assertTrue(userRepository.register(firstBidder));
    assertTrue(userRepository.register(secondBidder));

    Items item = new GeneralItem("item-4", "Speaker", "Portable speaker", seller);
    itemRepository.save(item);

    LocalDateTime start2 = LocalDateTime.now().minusMinutes(1);
    LocalDateTime end2 = LocalDateTime.now().plusSeconds(2);
    Auction auction = new Auction("auction-equal-bids", item, start2, end2, 100L);
    auction.setStatus(AuctionStatus.OPEN);
    auction.setApprovedBy("admin-1");
    auctionRepository.save(auction);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      // Act: hai bid bằng nhau chạy đồng thời; chỉ bid vào lock trước được nhận.
      Future<BidOutcome> firstBid = executor.submit(concurrentBid(
          bidRepository, ready, start, firstBidder.getId(), auction.getId(), 250, "bid-a"));
      Future<BidOutcome> secondBid = executor.submit(concurrentBid(
          bidRepository, ready, start, secondBidder.getId(), auction.getId(), 250, "bid-b"));

      assertTrue(ready.await(2, TimeUnit.SECONDS));
      start.countDown();

      BidOutcome firstOutcome = firstBid.get(5, TimeUnit.SECONDS);
      BidOutcome secondOutcome = secondBid.get(5, TimeUnit.SECONDS);
      long successfulBids = List.of(firstOutcome, secondOutcome).stream()
          .filter(BidOutcome::success)
          .count();
      assertEquals(1, successfulBids);
    } finally {
      executor.shutdownNow();
    }

    // Assert: lịch sử chỉ có một bid và tổng số dư chỉ bị trừ đúng một lần.
    Auction afterRace = auctionRepository.findById(auction.getId());
    assertEquals(250, afterRace.getHighestBid());
    assertNotNull(afterRace.getWinner());
    assertEquals(1, bidRepository.getBidsByAuctionId(auction.getId()).size());

    long totalBidderBalance = userRepository.findById(firstBidder.getId()).getBalance()
        + userRepository.findById(secondBidder.getId()).getBalance();
    assertEquals(1_750, totalBidderBalance);
  }

  private Callable<BidOutcome> concurrentBid(
      BidRepository bidRepository,
      CountDownLatch ready,
      CountDownLatch start,
      String userId,
      String auctionId,
      long amount,
      String bidId) {
    return () -> {
      ready.countDown();
      start.await();
      try {
        return new BidOutcome(
            bidRepository.executeBidTransaction(userId, auctionId, amount, bidId), null);
      } catch (RuntimeException e) {
        return new BidOutcome(false, e);
      }
    };
  }

  private record BidOutcome(boolean success, RuntimeException error) {}
}

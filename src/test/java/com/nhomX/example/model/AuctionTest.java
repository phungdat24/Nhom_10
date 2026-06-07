package com.nhomX.example.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.testsupport.FixtureFactory;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Kiểm thử rule lõi của phiên đấu giá ở mức unit.
 * Các ca dùng EP/BVA: trạng thái nhận bid, trạng thái đóng, phiên hết hạn và anti-snipe.
 */
class AuctionTest {
  private final RegularUser seller = FixtureFactory.seller("seller");
  private final GeneralItem item = FixtureFactory.item("item", seller);

  @Test
  void negativeStartingPriceIsRejected() {
    // BVA cho startingPrice: giá âm nằm ngoài miền hợp lệ nên phải ném exception.
    assertThrows(
        IllegalArgumentException.class,
        () -> new Auction("a1", item, LocalDateTime.now(), LocalDateTime.now(), -1));
  }

  @ParameterizedTest
  @MethodSource("bidAcceptanceCases")
  void canAcceptBidsDependsOnStatusAndTime(AuctionStatus status, boolean expected) {
    // EP theo trạng thái phiên: chỉ OPEN/RUNNING và chưa hết hạn mới thuộc lớp nhận bid.
    Auction auction = FixtureFactory.auction("a-" + status.name(), item, status, 100);

    assertEquals(expected, auction.canAcceptBids());
  }

  static Stream<Arguments> bidAcceptanceCases() {
    return Stream.of(
        Arguments.of(AuctionStatus.PENDING, false),
        Arguments.of(AuctionStatus.UP_COMING, false),
        Arguments.of(AuctionStatus.OPEN, true),
        Arguments.of(AuctionStatus.RUNNING, true),
        Arguments.of(AuctionStatus.FINISHED, false),
        Arguments.of(AuctionStatus.PAID, false),
        Arguments.of(AuctionStatus.CANCELED, false));
  }

  @Test
  void expiredAuctionCannotAcceptBidsEvenWhenOpen() {
    // Điều kiện trước: trạng thái OPEN nhưng endTime đã qua.
    // Oracle: thời gian hết hạn có quyền chặn bid dù status chưa được scheduler cập nhật.
    Auction auction = FixtureFactory.auction("expired", item, AuctionStatus.OPEN, 100);
    auction.setEndTime(LocalDateTime.now().minusSeconds(1));

    assertTrue(auction.isExpired());
    assertFalse(auction.canAcceptBids());
  }

  @Test
  void closeAuctionFinishesWhenWinnerExists() {
    // Phân hoạch đóng phiên có winner: kết quả mong đợi là FINISHED.
    Auction auction = FixtureFactory.auction("winner", item, AuctionStatus.RUNNING, 100);
    auction.setWinner(FixtureFactory.bidder("bidder", 500));

    auction.closeAuction();

    assertEquals(AuctionStatus.FINISHED, auction.getStatus());
  }

  @Test
  void closeAuctionCancelsWhenNoWinnerExists() {
    // Phân hoạch đóng phiên không có winner: kết quả mong đợi là CANCELED.
    Auction auction = FixtureFactory.auction("no-winner", item, AuctionStatus.RUNNING, 100);

    auction.closeAuction();

    assertEquals(AuctionStatus.CANCELED, auction.getStatus());
  }

  @ParameterizedTest
  @MethodSource("closedStatusCases")
  void isClosedRecognizesTerminalStates(AuctionStatus status, boolean expected) {
    // EP cho terminal state: FINISHED/PAID/CANCELED là lớp đã đóng, các trạng thái còn lại là chưa đóng.
    Auction auction = FixtureFactory.auction("closed-" + status.name(), item, status, 100);

    assertEquals(expected, auction.isClosed());
  }

  static Stream<Arguments> closedStatusCases() {
    return Stream.of(
        Arguments.of(AuctionStatus.PENDING, false),
        Arguments.of(AuctionStatus.UP_COMING, false),
        Arguments.of(AuctionStatus.OPEN, false),
        Arguments.of(AuctionStatus.RUNNING, false),
        Arguments.of(AuctionStatus.FINISHED, true),
        Arguments.of(AuctionStatus.PAID, true),
        Arguments.of(AuctionStatus.CANCELED, true));
  }

  @ParameterizedTest
  @EnumSource(
      value = AuctionStatus.class,
      names = {"OPEN", "RUNNING"})
  void determineWinnerClosesExpiredLiveAuction(AuctionStatus status) {
    // 1-way testing cho hai trạng thái live: OPEN và RUNNING đều phải được chốt khi đã hết hạn.
    Auction auction = FixtureFactory.auction("determine-" + status.name(), item, status, 100);
    auction.setWinner(FixtureFactory.bidder("winner-" + status.name(), 500));
    auction.setEndTime(LocalDateTime.now().minusSeconds(1));

    auction.determineWinner();

    assertEquals(AuctionStatus.FINISHED, auction.getStatus());
  }

  @Test
  void determineWinnerKeepsFutureAuctionOpen() {
    Auction auction = FixtureFactory.auction("future", item, AuctionStatus.OPEN, 100);
    auction.setWinner(FixtureFactory.bidder("winner", 500));

    auction.determineWinner();

    assertEquals(AuctionStatus.OPEN, auction.getStatus());
  }

  @Test
  void antiSnipeExtendsWhenBidIsNearEnd() {
    // BVA theo ngưỡng anti-snipe: còn 4 giây nằm trong biên cần gia hạn.
    Auction auction = FixtureFactory.auction("snipe", item, AuctionStatus.OPEN, 100);
    LocalDateTime originalEnd = LocalDateTime.now().plusSeconds(4);
    auction.setEndTime(originalEnd);

    auction.applyAntiSnipe();

    assertTrue(auction.getEndTime().isAfter(originalEnd));
  }

  @Test
  void antiSnipeDoesNotExtendWhenEnoughTimeRemains() {
    // Lớp tương đương ngoài ngưỡng: còn 1 phút nên không gia hạn.
    Auction auction = FixtureFactory.auction("not-snipe", item, AuctionStatus.OPEN, 100);
    LocalDateTime originalEnd = LocalDateTime.now().plusMinutes(1);
    auction.setEndTime(originalEnd);

    auction.applyAntiSnipe();

    assertEquals(originalEnd, auction.getEndTime());
  }

}

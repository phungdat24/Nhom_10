package com.nhomX.example.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.testsupport.FixtureFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Kiểm thử thuật toán tính giá tiếp theo của auto-bid.
 * Dùng BVA quanh maxLimit để tránh auto-bid vượt trần người dùng đặt.
 */
class AutoBidConfigTest {
  @Test
  void constructorInitializesValidActiveConfig() {
    // Điều kiện trước: phiên OPEN và bidder hợp lệ.
    // Oracle: config mới active, có maxLimit và increment đúng input.
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);

    AutoBidConfig config = new AutoBidConfig("config", 500, 50, bidder, auction);

    assertEquals("config", config.getId());
    assertEquals(500, config.getMaxLimit());
    assertEquals(50, config.getIncrement());
    assertNotNull(config.getCreatedAt());
    assertTrue(config.isActive());
  }

  @ParameterizedTest
  @MethodSource("nextBidCases")
  void computeNextBidRespectsLimit(long currentPrice, long maxLimit, long increment, long expected) {
    // BVA quanh maxLimit: dưới trần, sát trần, bằng trần và vượt trần.
    AutoBidConfig config =
        new AutoBidConfig(
            "config",
            maxLimit,
            increment,
            FixtureFactory.bidder("bidder", 1_000),
            FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100));

    assertEquals(expected, config.computeNextBid(currentPrice));
  }

  static Stream<Arguments> nextBidCases() {
    return Stream.of(
        Arguments.of(100, 500, 50, 150),
        Arguments.of(480, 500, 50, 500),
        Arguments.of(500, 500, 50, -1),
        Arguments.of(600, 500, 50, -1));
  }

  @Test
  void invalidLimitAndIncrementAreRejected() {
    // Phân hoạch lỗi đầu vào: maxLimit/increment <= 0 không hợp lệ.
    AutoBidConfig config = new AutoBidConfig();

    assertThrows(IllegalArgumentException.class, () -> config.setMaxLimit(0));
    assertThrows(IllegalArgumentException.class, () -> config.setIncrement(0));
  }

  @Test
  void deactivateStopsActiveConfig() {
    // Oracle vòng đời: sau khi người dùng tắt auto-bid, config không còn active.
    AutoBidConfig config =
        new AutoBidConfig(
            "config",
            500,
            50,
            FixtureFactory.bidder("bidder", 1_000),
            FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100));

    config.deactivate();

    assertFalse(config.isActive());
  }

  @Test
  void closedAuctionMakesConfigInactive() {
    // Điều kiện trước: phiên đã FINISHED, nên config không được kích hoạt dù bản thân chưa deactivate.
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);
    auction.setStatus(AuctionStatus.FINISHED);
    AutoBidConfig config =
        new AutoBidConfig("config", 500, 50, FixtureFactory.bidder("bidder", 1_000), auction);

    assertFalse(config.isActive());
  }

}

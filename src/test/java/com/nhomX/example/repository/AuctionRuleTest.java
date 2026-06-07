package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionRuleTest {
  @Test
  void cannotBidAfterEndTime() {
    // Arrange: phiên vẫn OPEN nhưng thời gian kết thúc đã qua.
    RegularUser seller = new RegularUser("seller", "seller@example.com", "hash", "Seller", 0,true);
    GeneralItem item = new GeneralItem("item", "Clock", "Vintage clock", seller);
    LocalDateTime start = LocalDateTime.now().minusMinutes(10); // Cho thời gian bắt đầu là 10 phút trước
    LocalDateTime end = LocalDateTime.now().minusSeconds(1);
    Auction auction = new Auction("auction", item, start, end, 100L);
    auction.setStatus(AuctionStatus.OPEN);

    RegularUser bidder = new RegularUser("bidder", "bidder@example.com", "hash", "Bidder", 500, true);
    bidder.addRole(Role.BIDDER);

    // Assert: logic domain phải chặn bid dù status chưa kịp đổi sang FINISHED.
    assertFalse(auction.canAcceptBids());
    assertThrows(IllegalStateException.class, () -> bidder.placeBid(auction, 150));
  }
}

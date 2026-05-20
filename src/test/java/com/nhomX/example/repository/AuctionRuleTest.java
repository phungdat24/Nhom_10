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
    RegularUser seller = new RegularUser("seller", "seller@example.com", "hash", "Seller", 0);
    GeneralItem item = new GeneralItem("item", "Clock", "Vintage clock", seller);
    Auction auction = new Auction("auction", item, LocalDateTime.now().minusSeconds(1), 100);
    auction.setStatus(AuctionStatus.OPEN);

    RegularUser bidder = new RegularUser("bidder", "bidder@example.com", "hash", "Bidder", 500);
    bidder.addRole(Role.BIDDER);

    // Assert: logic domain phải chặn bid dù status chưa kịp đổi sang FINISHED.
    assertFalse(auction.canAcceptBids());
    assertThrows(IllegalStateException.class, () -> bidder.placeBid(auction, 150));
  }
}

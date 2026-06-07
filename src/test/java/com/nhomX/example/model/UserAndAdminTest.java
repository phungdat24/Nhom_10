package com.nhomX.example.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.testsupport.FixtureFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAndAdminTest {
  @Test
  void updateBalanceAddsAndSubtracts() {
    RegularUser user = FixtureFactory.bidder("bidder", 500);

    user.updateBalance(200);
    user.updateBalance(-150);

    assertEquals(550, user.getBalance());
  }

  @Test
  void updateBalanceRejectsOverdraft() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    assertThrows(IllegalArgumentException.class, () -> user.updateBalance(-101));
    assertEquals(100, user.getBalance());
  }

  @Test
  void setBalanceRejectsNegativeValue() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    assertThrows(IllegalArgumentException.class, () -> user.setBalance(-1));
  }

  @Test
  void accountLockAndUnlockChangeActiveFlag() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    user.lockAccount();
    assertFalse(user.isActive());

    user.unlockAccount();
    assertTrue(user.isActive());
  }

  @Test
  void regularUserRolesAreUniqueAndRemovable() {
    RegularUser user = new RegularUser();

    user.addRole(Role.BIDDER);
    user.addRole(Role.BIDDER);
    user.addRole(Role.SELLER);
    user.removeRole(Role.BIDDER);

    assertFalse(user.hasRole(Role.BIDDER));
    assertTrue(user.hasRole(Role.SELLER));
    assertEquals(1, user.getRoles().size());
  }

  @Test
  void emptyRoleNameIsGuest() {
    RegularUser user = new RegularUser();

    assertEquals("GUEST", user.getRoleName());
  }

  @Test
  void roleNameIncludesAllAssignedRoles() {
    RegularUser user = FixtureFactory.regularUser("u1", "u1@example.com", 0, Role.BIDDER, Role.SELLER);

    assertTrue(user.getRoleName().contains("BIDDER"));
    assertTrue(user.getRoleName().contains("SELLER"));
  }

  @Test
  void setRolesReplacesRoleSet() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    user.setRoles(Set.of(Role.SELLER));

    assertFalse(user.hasRole(Role.BIDDER));
    assertTrue(user.hasRole(Role.SELLER));
  }

  @Test
  void activeBidderCanCreateBidTransaction() {
    RegularUser seller = FixtureFactory.seller("seller");
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", seller, 100);

    BidTransaction bid = bidder.placeBid(auction, 150);

    assertNotNull(bid.getId());
    assertEquals(150, bid.getAmount());
    assertSame(bidder, bid.getBidder());
    assertSame(auction, bid.getAuction());
    assertNotNull(bid.getBidTime());
  }

  @Test
  void inactiveUserCannotPlaceBid() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    bidder.lockAccount();

    assertThrows(
        IllegalStateException.class,
        () -> bidder.placeBid(FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100), 150));
  }

  @Test
  void userWithoutBidderRoleCannotPlaceBid() {
    RegularUser user = FixtureFactory.seller("seller");

    assertThrows(
        IllegalStateException.class,
        () -> user.placeBid(FixtureFactory.openAuction("auction", user, 100), 150));
  }

  @Test
  void closedAuctionRejectsDomainBid() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);
    auction.setStatus(AuctionStatus.FINISHED);

    assertThrows(IllegalStateException.class, () -> bidder.placeBid(auction, 150));
  }

  @Test
  void bidMustBeGreaterThanHighestBid() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);

    assertThrows(IllegalArgumentException.class, () -> bidder.placeBid(auction, 100));
  }

  @Test
  void bidderCanCreateAutoBidConfig() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);

    AutoBidConfig config = bidder.setupAutoBid(auction, 500, 50);

    assertNotNull(config.getId());
    assertSame(bidder, config.getBidder());
    assertSame(auction, config.getAuction());
    assertEquals(500, config.getMaxLimit());
    assertEquals(50, config.getIncrement());
  }

  @Test
  void autoBidRequiresBidderRole() {
    RegularUser seller = FixtureFactory.seller("seller");

    assertThrows(
        IllegalStateException.class,
        () -> seller.setupAutoBid(FixtureFactory.openAuction("auction", seller, 100), 500, 50));
  }

  @Test
  void autoBidRequiresOpenAuction() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);
    auction.setStatus(AuctionStatus.CANCELED);

    assertThrows(IllegalStateException.class, () -> bidder.setupAutoBid(auction, 500, 50));
  }

  @Test
  void autoBidLimitMustBeatCurrentPrice() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);

    assertThrows(IllegalArgumentException.class, () -> bidder.setupAutoBid(auction, 100, 10));
  }

  @Test
  void sellerCanAttachProductToSelf() {
    RegularUser seller = FixtureFactory.seller("seller");
    GeneralItem item = new GeneralItem();

    seller.addProduct(item);

    assertSame(seller, item.getSeller());
  }

  @Test
  void nonSellerCannotAddProduct() {
    RegularUser bidder = FixtureFactory.bidder("bidder", 1_000);

    assertThrows(IllegalStateException.class, () -> bidder.addProduct(new GeneralItem()));
  }

  @Test
  void inactiveSellerCannotAddProduct() {
    RegularUser seller = FixtureFactory.seller("seller");
    seller.lockAccount();

    assertThrows(IllegalStateException.class, () -> seller.addProduct(new GeneralItem()));
  }

  @Test
  void ownerCanCloseOwnAuction() {
    RegularUser seller = FixtureFactory.seller("seller");
    Auction auction = FixtureFactory.openAuction("auction", seller, 100);

    seller.closeAuction(auction);

    assertEquals(AuctionStatus.CANCELED, auction.getStatus());
  }

  @Test
  void nonOwnerCannotCloseAuction() {
    RegularUser seller = FixtureFactory.seller("seller");
    RegularUser other = FixtureFactory.seller("other");
    Auction auction = FixtureFactory.openAuction("auction", seller, 100);

    assertThrows(IllegalStateException.class, () -> other.closeAuction(auction));
  }

  @Test
  void adminRoleNameIsAdmin() {
    assertEquals("ADMIN", FixtureFactory.admin("admin").getRoleName());
  }

  @Test
  void adminCanCancelOpenAuction() {
    Admin admin = FixtureFactory.admin("admin");
    Auction auction = FixtureFactory.openAuction("auction", FixtureFactory.seller("seller"), 100);

    admin.forceCancelAuction(auction, "test");

    assertEquals(AuctionStatus.CANCELED, auction.getStatus());
  }

  @Test
  void adminCannotCancelFinishedOrPaidAuction() {
    Admin admin = FixtureFactory.admin("admin");
    Auction finished = FixtureFactory.openAuction("finished", FixtureFactory.seller("seller"), 100);
    finished.setStatus(AuctionStatus.FINISHED);
    Auction paid = FixtureFactory.openAuction("paid", FixtureFactory.seller("seller"), 100);
    paid.setStatus(AuctionStatus.PAID);

    assertThrows(IllegalStateException.class, () -> admin.forceCancelAuction(finished, "test"));
    assertThrows(IllegalStateException.class, () -> admin.forceCancelAuction(paid, "test"));
  }

  @Test
  void adminCanBanUser() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    FixtureFactory.admin("admin").banUser(user);

    assertFalse(user.isActive());
  }

  @Test
  void adminCanAddFundsToUser() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    FixtureFactory.admin("admin").addFundsToUser(user, 50);

    assertEquals(150, user.getBalance());
  }

  @Test
  void adminRejectsNonPositiveFunds() {
    RegularUser user = FixtureFactory.bidder("bidder", 100);

    assertThrows(IllegalArgumentException.class, () -> FixtureFactory.admin("admin").addFundsToUser(user, 0));
  }

  @Test
  void adminApprovesOnlyPendingAuction() {
    Admin admin = FixtureFactory.admin("admin");
    Auction pending = FixtureFactory.openAuction("pending", FixtureFactory.seller("seller"), 100);
    pending.setStatus(AuctionStatus.PENDING);
    Auction alreadyOpen = FixtureFactory.openAuction("open", FixtureFactory.seller("seller"), 100);

    admin.approveAuction(pending);

    assertEquals(AuctionStatus.OPEN, pending.getStatus());
    assertThrows(IllegalStateException.class, () -> admin.approveAuction(alreadyOpen));
  }
}

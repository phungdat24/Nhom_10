package com.nhomX.example.testsupport;

import com.nhomX.example.model.Admin;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.utils.SecurityUtils;
import java.time.LocalDateTime;

public final class FixtureFactory {
  private FixtureFactory() {}

  public static RegularUser regularUser(String id, String email, long balance, Role... roles) {
    RegularUser user =
        new RegularUser(id, email, SecurityUtils.hashPassword("password"), email, balance, true);
    for (Role role : roles) {
      user.addRole(role);
    }
    return user;
  }

  public static RegularUser seller(String id) {
    return regularUser(id, id + "@example.com", 0, Role.SELLER);
  }

  public static RegularUser bidder(String id, long balance) {
    return regularUser(id, id + "@example.com", balance, Role.BIDDER);
  }

  public static Admin admin(String id) {
    return new Admin(
        id,
        id + "@example.com",
        SecurityUtils.hashPassword("password"),
        "Admin " + id,
        0,
        true);
  }

  public static GeneralItem item(String id, RegularUser seller) {
    return new GeneralItem(id, "Item " + id, "Description " + id, seller);
  }

  public static Auction auction(String id, Items item, AuctionStatus status, long startingPrice) {
    Auction auction =
        new Auction(
            id,
            item,
            LocalDateTime.now().minusMinutes(10),
            LocalDateTime.now().plusMinutes(30),
            startingPrice);
    auction.setStatus(status);
    return auction;
  }

  public static Auction openAuction(String id, RegularUser seller, long startingPrice) {
    return auction(id, item("item-" + id, seller), AuctionStatus.OPEN, startingPrice);
  }
}

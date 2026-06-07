package com.nhomX.example.testsupport;

import com.nhomX.example.model.Admin;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.utils.DatabaseConnection;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class DatabaseBackedTest {
  @TempDir
  protected Path tempDir;

  @BeforeEach
  protected void openIsolatedDatabase() {
    Path databasePath = tempDir.resolve("auction-test.db").toAbsolutePath();
    System.setProperty(DatabaseConnection.URL_PROPERTY, "jdbc:sqlite:" + databasePath);
    DatabaseConnection.resetForTests();
    DatabaseConnection.getInstance();
  }

  @AfterEach
  protected void closeIsolatedDatabase() {
    DatabaseConnection.resetForTests();
    System.clearProperty(DatabaseConnection.URL_PROPERTY);
  }

  protected RegularUser regularUser(String id, String email, long balance, Role... roles) {
    return FixtureFactory.regularUser(id, email, balance, roles);
  }

  protected RegularUser seller(String id) {
    return FixtureFactory.seller(id);
  }

  protected RegularUser bidder(String id, long balance) {
    return FixtureFactory.bidder(id, balance);
  }

  protected Admin admin(String id) {
    return FixtureFactory.admin(id);
  }

  protected GeneralItem item(String id, RegularUser seller) {
    return FixtureFactory.item(id, seller);
  }

  protected Auction auction(String id, Items item, AuctionStatus status, long startingPrice) {
    return FixtureFactory.auction(id, item, status, startingPrice);
  }

  protected Auction datedAuction(
      String id,
      Items item,
      AuctionStatus status,
      LocalDateTime startTime,
      LocalDateTime endTime,
      long startingPrice) {
    Auction auction = new Auction(id, item, startTime, endTime, startingPrice);
    auction.setStatus(status);
    return auction;
  }

  protected void saveUsers(UserRepository repository, User... users) {
    for (User user : users) {
      repository.register(user);
    }
  }

  protected void saveItems(ItemRepository repository, Items... items) {
    for (Items item : items) {
      repository.save(item);
    }
  }

  protected void saveAuctions(AuctionRepository repository, Auction... auctions) {
    for (Auction auction : auctions) {
      repository.save(auction);
    }
  }
}

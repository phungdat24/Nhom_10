package com.nhomX.example.repository;

import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.utils.DatabaseConnection;
import com.nhomX.example.utils.SecurityUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class DatabaseBackedTest {
  @TempDir
  protected Path tempDir;

  @BeforeEach
  protected void openIsolatedDatabase() {
    // Mỗi test chạy trên file SQLite tạm để không làm bẩn auction.db thật.
    Path databasePath = tempDir.resolve("auction-test.db").toAbsolutePath();
    System.setProperty(DatabaseConnection.URL_PROPERTY, "jdbc:sqlite:" + databasePath);
    DatabaseConnection.resetForTests();
    DatabaseConnection.getInstance();
  }

  @AfterEach
  protected void closeIsolatedDatabase() {
    // Đóng singleton DB để test sau có database sạch hoàn toàn.
    DatabaseConnection.resetForTests();
    System.clearProperty(DatabaseConnection.URL_PROPERTY);
  }

  protected RegularUser regularUser(String id, String email, long balance, Role... roles) {
    RegularUser user =
        new RegularUser(id, email, SecurityUtils.hashPassword("password"), email, balance);
    for (Role role : roles) {
      user.addRole(role);
    }
    return user;
  }
}

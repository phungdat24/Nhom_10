package com.nhomX.example.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhomX.example.model.Admin;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.testsupport.DatabaseBackedTest;
import com.nhomX.example.utils.SecurityUtils;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đăng ký/đăng nhập và các nghiệp vụ tài khoản.
 * Theo tài liệu 8.1, mỗi test có input kiểm soát, trạng thái database tạm và oracle bằng assertion.
 */
class UserRepositoryImplTest extends DatabaseBackedTest {
  private final UserRepository repository = new UserRepositoryImpl();

  @Test
  void registerAndLoginRegularUserPersistsRolesAndActiveFlag() {
    // Input: user thường có cả BIDDER và SELLER.
    // Oracle: đăng ký thành công, đăng nhập trả đúng loại user và role được khôi phục từ DB.
    RegularUser user = regularUser("u1", "u1@example.com", 500, Role.BIDDER, Role.SELLER);

    assertTrue(repository.register(user));
    User loggedIn = repository.login("u1@example.com", SecurityUtils.hashPassword("password"));

    assertInstanceOf(RegularUser.class, loggedIn);
    RegularUser regularUser = (RegularUser) loggedIn;
    assertEquals("u1", regularUser.getId());
    assertTrue(regularUser.hasRole(Role.BIDDER));
    assertTrue(regularUser.hasRole(Role.SELLER));
    assertTrue(regularUser.isActive());
  }

  @Test
  void duplicateUsernameIsRejectedByUniqueConstraint() {
    // Phân hoạch lỗi đăng ký: email/username trùng phải bị từ chối bởi unique constraint.
    RegularUser first = regularUser("u1", "duplicate@example.com", 100, Role.BIDDER);
    RegularUser second = regularUser("u2", "duplicate@example.com", 100, Role.SELLER);

    assertTrue(repository.register(first));
    assertFalse(repository.register(second));
    assertEquals(1, repository.getTotalUserCount());
  }

  @Test
  void adminIsMappedBackAsAdmin() {
    // Oracle đăng nhập/quản trị: role ADMIN trong DB phải map lại thành Admin, không thành RegularUser.
    Admin admin = admin("admin");

    assertTrue(repository.register(admin));

    assertInstanceOf(Admin.class, repository.findById("admin"));
    assertInstanceOf(Admin.class, repository.findByUsername("admin@example.com"));
  }

  @Test
  void findAllAndCountReturnPersistedUsers() {
    saveUsers(repository, regularUser("u1", "u1@example.com", 100, Role.BIDDER), admin("admin"));

    assertEquals(2, repository.findAll().size());
    assertEquals(2, repository.getTotalUserCount());
  }

  @Test
  void updateBalanceDepositsAndPreventsOverdraft() {
    // BVA tài chính: cộng tiền hợp lệ, sau đó thử rút vượt số dư và kiểm tra số dư không âm.
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));

    repository.updateBalance("u1", 50);
    repository.updateBalance("u1", -200);

    assertEquals(150, repository.findById("u1").getBalance());
  }

  @Test
  void withdrawBalanceValidatesInputAndFunds() {
    // Phân hoạch đầu vào rút tiền: userId null/blank, amount <= 0, thiếu tiền, và trường hợp hợp lệ.
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));

    assertFalse(repository.withdrawBalance(null, 10));
    assertFalse(repository.withdrawBalance(" ", 10));
    assertFalse(repository.withdrawBalance("u1", 0));
    assertFalse(repository.withdrawBalance("u1", 101));
    assertTrue(repository.withdrawBalance("u1", 40));

    assertEquals(60, repository.findById("u1").getBalance());
  }

  @Test
  void updatePasswordChangesLoginCredential() {
    // Oracle đăng nhập: mật khẩu cũ không còn dùng được, mật khẩu mới đăng nhập thành công.
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));

    assertTrue(repository.updatePassword("u1@example.com", SecurityUtils.hashPassword("new-password")));

    assertNull(repository.login("u1@example.com", SecurityUtils.hashPassword("password")));
    assertEquals("u1", repository.login("u1@example.com", SecurityUtils.hashPassword("new-password")).getId());
  }

  @Test
  void activeStatusCanBeChangedAndMissingUserDefaultsActive() {
    // Kiểm tra trạng thái khóa/mở khóa tài khoản và hành vi mặc định khi user không tồn tại.
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));

    assertTrue(repository.setUserActiveStatus("u1", false));
    assertFalse(repository.isUserActive("u1"));
    assertTrue(repository.setUserActiveStatus("u1", true));
    assertTrue(repository.isUserActive("u1"));
    assertFalse(repository.setUserActiveStatus("missing", false));
    assertTrue(repository.isUserActive("missing"));
  }

  @Test
  void updatePersistsMutableUserFields() {
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));
    user.setFullName("Updated Name");
    user.setPasswordHash(SecurityUtils.hashPassword("updated"));
    user.setBalance(250);
    user.addRole(Role.SELLER);
    user.lockAccount();

    repository.update(user);

    RegularUser updated = (RegularUser) repository.findById("u1");
    assertEquals("Updated Name", updated.getFullName());
    assertEquals(250, updated.getBalance());
    assertTrue(updated.hasRole(Role.SELLER));
    assertFalse(updated.isActive());
  }

  @Test
  void deleteUserRemovesExistingUserAndReportsMissingUser() {
    RegularUser user = regularUser("u1", "u1@example.com", 100, Role.BIDDER);
    assertTrue(repository.register(user));

    assertTrue(repository.deleteUser("u1"));
    assertNull(repository.findById("u1"));
    assertFalse(repository.deleteUser("u1"));
  }
}

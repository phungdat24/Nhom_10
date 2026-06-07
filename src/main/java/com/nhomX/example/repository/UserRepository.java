package com.nhomX.example.repository;

import java.util.List;
import com.nhomX.example.model.User;

// Interface Quản lý người dùng:
public interface UserRepository {
    // Trả về đối tượng User nếu đúng thông tin:
    User login(String userName, String passWord);

    // Thêm một User mới:
    boolean register(User user);

    // Tính năng nạp/rút tiền:
    void updateBalance(String userId, long deltaAmount);

    // Lấy thông tin User:
    User findById(String userId);
    // Tìm user theo username (dùng khi kiểm tra username trùng lúc đăng ký).

    User findByUsername(String username);

    // Lấy toàn bộ danh sách user (dùng cho Admin quản lý tài khoản).
    List<User> findAll();

    int getTotalUserCount();
    // Update khi quên mật khẩu:
     boolean updatePassword(String username, String newPasswordHash);

    /**
     * Lấy toàn bộ danh sách user (đã có sẵn findAll()).
     * Thêm 3 method mới phục vụ Admin:
     */

    /**
     * Khóa/mở khóa tài khoản user.
     *
     * @param userId ID user cần thay đổi
     * @param isActive true = mở khóa, false = khóa
     * @return true nếu cập nhật thành công
     */
    boolean setUserActiveStatus(String userId, boolean isActive);

    /**
     * Xóa vĩnh viễn một user khỏi hệ thống.
     *
     * @param userId ID user cần xóa
     * @return true nếu xóa thành công
     */
    boolean deleteUser(String userId);

    /**
     * Kiểm tra trạng thái active của user.
     *
     * @param userId ID user cần kiểm tra
     * @return true nếu đang active
     */
    boolean isUserActive(String userId);
    // Bao gồm updateBalance, updatePassword, setUserActiveStatus và isUserActive
    void update(User user);
}

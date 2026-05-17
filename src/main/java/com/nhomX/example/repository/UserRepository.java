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
}

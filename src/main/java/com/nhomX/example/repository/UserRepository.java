package com.nhomX.example.repository;

import com.nhomX.example.model.User;
// Interface Quản lý người dùng:
public interface UserRepository {
    // Trả về đối tượng User nếu đúng thông tin:
    User login(String userName, String passWord);
    // Thêm một User mới:
    boolean register(User user);
    // Tính năng nạp/rút tiền:
    void updateBalance(String userId, double amount);
    // Lấy thông tin User:
    User findById(String userId);
}

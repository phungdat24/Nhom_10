package com.nhomX.example.repository;

import com.nhomX.example.model.User;

public class UserRepositoryImpl implements UserRepository{
    @Override
    // Tạo Database giả
    public User login(String userName, String passWord) {
        if("admin".equals(userName) && "123".equals(passWord)){
            User mockUser = new User();
            mockUser.setUserName("admin");
            mockUser.setFullName("Phùng Tiến Đạt"); // Rất quan trọng cho Dashboard
            mockUser.setBalance(50000000.0);

            System.out.println("Đã lấy được User từ DB giả!");
            return mockUser;
        }
        return null;
    }
    public boolean register(User user){
       return true;
    }
    // Tính năng nạp/rút tiền:
    public void updateBalance(String userId, double amount){

    }
    // Lấy thông tin User:
    public User findById(String userId){
        return null;
    }
}

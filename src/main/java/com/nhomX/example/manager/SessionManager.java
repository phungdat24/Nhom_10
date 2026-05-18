package com.nhomX.example.manager;

import com.nhomX.example.model.User;
import com.nhomX.example.networking.AuctionClient;

/**
 * SessionManager — Singleton
 * Lưu thông tin User đang đăng nhập, dùng chung toàn bộ ứng dụng.
 * Không phân biệt role: một User vừa có thể đấu giá, vừa có thể bán hàng.
 */
public class SessionManager {

    private volatile static SessionManager instance;

    private AuctionClient auctionClient;

    private User currentUser; // null = chưa đăng nhập

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            // Khóa luồng:
            synchronized (SessionManager.class){
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }
    // Đăng nhập — lưu User lấy từ Server vào session
    public void login(User user) {
        this.currentUser = user;
    }
    // Đăng xuất — xóa User khỏi session
    public void logout() {
        this.currentUser = null;
    }
    // Kiểm tra trạng thái
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    // Lấy User hiện tại (trả về null nếu chưa đăng nhập)
    public User getCurrentUser() {
        return currentUser;
    }
    public AuctionClient getAuctionClient(){
        return auctionClient;
    }

    public void setAuctionClient(AuctionClient auctionClient) {
        this.auctionClient = auctionClient;
    }
}

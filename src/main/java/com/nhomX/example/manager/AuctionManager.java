package com.nhomX.example.manager;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.RegularUser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lớp Singleton quản lý bộ nhớ đệm (Cache) danh sách đấu giá tại Client.
 * Đóng vai trò là Nguồn Sự Thật Duy Nhất (Single Source of Truth) cho toàn bộ Giao diện UI.
 */
public class AuctionManager {
    // 1. Khởi tạo Singleton an toàn đa luồng (Thread-safe) với từ khóa volatile
    private static volatile AuctionManager instance;

    // 2. Bộ nhớ Cache lõi: Dùng ConcurrentHashMap để Thread Socket vừa ghi, Thread UI vừa đọc mà không Crash
    private final ConcurrentHashMap<String, Auction> auctionCache;

    // Chặn khởi tạo bằng từ khóa new
    private AuctionManager() {
        this.auctionCache = new ConcurrentHashMap<>();
    }

    // Double-Checked Locking Singleton
    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // ========================================================================
    // CÁC HÀM NẠP DỮ LIỆU TỪ SERVER VÀO CACHE (Ghi dữ liệu)
    // ========================================================================

    /**
     * Nạp toàn bộ danh sách từ Server tải về vào Cache.
     * Thường gọi một lần khi Client vừa kết nối hoặc người dùng bấm nút "Làm mới".
     */
    public void setAllAuctions(List<Auction> auctions) {
        if (auctions == null) return;
        auctionCache.clear(); // Xóa rác cũ
        for (Auction auction : auctions) {
            auctionCache.put(auction.getId(), auction);
        }
        System.out.println("CACHE: Đã nạp thành công " + auctions.size() + " phiên đấu giá vào bộ nhớ tạm.");
    }

    /**
     * Cập nhật thời gian thực khi có người đấm búa (Real-time Price Update).
     */
    public void updateAuctionPrice(String auctionId, long newPrice, String winnerId, String winnerName) {
        Auction auction = auctionCache.get(auctionId);
        if (auction != null) {
            auction.setHighestBid(newPrice);

            // Cập nhật người dẫn đầu tạm thời
            if (winnerId != null) {
                RegularUser tempWinner = new RegularUser();
                tempWinner.setId(winnerId);
                tempWinner.setFullName(winnerName);
                auction.setWinner(tempWinner);
            }
        }
    }

    /**
     * Cập nhật trạng thái khi Server thông báo đóng phiên (Real-time Status Update).
     */
    public void closeAuctionInCache(String auctionId, String winnerId) {
        Auction auction = auctionCache.get(auctionId);
        if (auction != null) {
            auction.setStatus(AuctionStatus.FINISHED);
            // Nếu có winnerId, cập nhật lại ID (Tên có thể null ở bước này, UI tự xử lý)
            if (winnerId != null && !winnerId.isEmpty()) {
                if (auction.getWinner() == null) {
                    auction.setWinner(new RegularUser());
                }
                auction.getWinner().setId(winnerId);
            }
        }
    }
    // CÁC HÀM CUNG CẤP DỮ LIỆU CHO GIAO DIỆN JAVAFX (Đọc dữ liệu)
    /**
     * Lấy toàn bộ danh sách để vẽ lên màn hình Dashboard.
     */
    public List<Auction> getAllCachedAuctions() {
        return new ArrayList<>(auctionCache.values());
    }

    /**
     * Lấy ra đúng những phiên đang hiển thị cho phép đấu giá (OPEN, RUNNING).
     */
    public List<Auction> getActiveAuctions() {
        List<Auction> activeList = new ArrayList<>();
        for (Auction a : auctionCache.values()) {
            if (a.getStatus() == AuctionStatus.OPEN || a.getStatus() == AuctionStatus.RUNNING) {
                activeList.add(a);
            }
        }
        return activeList;
    }

    /**
     * Lấy chi tiết 1 phiên cụ thể (Dùng cho màn hình Chi tiết sản phẩm).
     */
    public Auction getAuctionById(String auctionId) {
        return auctionCache.get(auctionId);
    }

    /**
     * Dọn dẹp Cache (Thường gọi khi Đăng xuất).
     */
    public void clearCache() {
        auctionCache.clear();
    }
}

package com.nhomX.example.manager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.RegularUser;

/**
 * Lớp Singleton quản lý bộ nhớ đệm (Cache) danh sách đấu giá tại Client. Đóng vai trò là Nguồn Sự
 * Thật Duy Nhất (Single Source of Truth) cho toàn bộ Giao diện UI.
 */
public class AuctionManager {
    private static final Logger logger = LoggerFactory.getLogger(AuctionManager.class);
    // 1. Khởi tạo Singleton an toàn đa luồng (Thread-safe) với từ khóa volatile
    private static volatile AuctionManager instance;

    // 2. Bộ nhớ Cache lõi: Dùng ConcurrentHashMap để Thread Socket vừa ghi, Thread UI vừa đọc mà
    // không Crash
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
     * Nạp toàn bộ danh sách từ Server tải về vào Cache. Thường gọi một lần khi Client vừa kết nối
     * hoặc người dùng bấm nút "Làm mới".
     */
    public void setAllAuctions(List<Auction> auctions) {
        if (auctions == null)
            return;
        auctionCache.clear(); // Xóa rác cũ
        for (Auction auction : auctions) {
            auctionCache.put(auction.getId(), auction);
        }
        logger.info("CACHE: Đã nạp thành công {} phiên đấu giá vào bộ nhớ tạm.", auctions.size());
    }

    /**
     * Cập nhật thời gian thực khi có người đấm búa (Real-time Price Update).
     */
    public void updateAuctionPrice(String auctionId, long newPrice, String winnerId,
            String winnerName) {
        Auction auction = auctionCache.get(auctionId);
        if (auction != null) {
            // Chỉ cập nhật nếu giá mới CAO HƠN giá hiện tại
            if (newPrice >= auction.getHighestBid()) {
                auction.setHighestBid(newPrice);
                if (winnerId != null) {
                    RegularUser tempWinner = new RegularUser();
                    tempWinner.setId(winnerId);
                    tempWinner.setFullName(winnerName);
                    auction.setWinner(tempWinner);
                }
            } else {
                logger.warn("CACHE WARNING: Bỏ qua gói tin cũ - giá {} thấp hơn giá hiện tại {}",
                        newPrice, auction.getHighestBid());
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
    // ✅ FIX getAllCachedAuctions(): Trả về bản sao có thứ tự ổn định
    // ConcurrentHashMap không đảm bảo thứ tự → sort theo ID để UI không bị nhảy loạn
    public List<Auction> getAllCachedAuctions() {
        List<Auction> list = new ArrayList<>(auctionCache.values());
        list.sort(Comparator.comparing(Auction::getId));
        return list;
    }

    /**
     * Lấy ra đúng những phiên đang hiển thị cho phép đấu giá (OPEN, RUNNING). ĐIỀU KIỆN HỢP LỆ (3
     * tiêu chí phải thỏa đồng thời): 1. Status phải là OPEN hoặc RUNNING 2. startTime <= now (phiên
     * đã bắt đầu) 3. endTime > now (phiên chưa kết thúc)
     */
    public List<Auction> getActiveAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> activeList = new ArrayList<>();

        for (Auction a : auctionCache.values()) {
            // Tiêu chí 1: Lọc theo trạng thái
            boolean validStatus =
                    a.getStatus() == AuctionStatus.OPEN || a.getStatus() == AuctionStatus.RUNNING;
            if (!validStatus)
                continue;

            // Tiêu chí 2: Phiên phải đã bắt đầu
            boolean hasStarted = a.getStartTime() != null && !a.getStartTime().isAfter(now);

            // Tiêu chí 3: Phiên chưa kết thúc
            boolean notExpired = a.getEndTime() != null && a.getEndTime().isAfter(now);

            if (hasStarted && notExpired) {
                activeList.add(a);
            }
        }
        return activeList;
    }

    /**
     * Cập nhật hoặc thêm mới một danh sách các phiên đấu giá vào Cache MÀ KHÔNG XÓA đi các phiên
     * đang có sẵn trong RAM. (Thao tác: Upsert = Update or Insert)
     */
    public void updateOrAddAuctions(List<Auction> auctions) {
        if (auctions == null || auctions.isEmpty())
            return;

        for (Auction auction : auctions) {
            // Hàm put của ConcurrentHashMap cực kỳ thông minh:
            // - Nếu ID đã tồn tại: Nó ghi đè Object cũ bằng Object mới này.
            // - Nếu ID chưa tồn tại: Nó thêm Object này vào thành một mục mới.
            auctionCache.put(auction.getId(), auction);
        }
        logger.info("CACHE: Đã cập nhật (Merge) {} phiên đấu giá vào bộ nhớ an toàn.",
                auctions.size());
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

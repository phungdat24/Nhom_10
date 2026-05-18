package com.nhomX.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    // Tham chiếu đến món đồ đang được đấu giá
    private Items item;
    // Thời điểm bắt đâu đấu giá:
    private LocalDateTime startTime;
    // Thời điểm kết thúc phiên
    private LocalDateTime endTime;

    // Trạng thái hiện tại của phiên (OPEN, RUNNING, FINISHED...)
    private AuctionStatus status;

    // Người đang giữ mức giá cao nhất hoặc người thắng cuộc cuối cùng
    private RegularUser winner;

    // Mức giá cao nhất hiện tại của phiên
    private long highestBid;
    private String approvedBy;
    // [THÊM MỚI] Danh sách observers để hỗ trợ Observer Pattern (realtime update)
    private transient List<AuctionObserver> observers = new ArrayList<>();

    // [THÊM MỚI] Anti-sniping: ngưỡng thời gian và thời lượng gia hạn
    private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 5;
    private static final int ANTI_SNIPE_EXTENSION_SECONDS = 5;

    // 1. Hàm tạo rỗng (Phục vụ cho việc load dữ liệu từ Database)
    public Auction() {
        super();
        // Mặc định khi mới tạo
        this.status = AuctionStatus.PENDING;
        this.observers= new ArrayList<>();
    }

    // 2. Hàm tạo đầy đủ tham số
    public Auction(String id, Items item, LocalDateTime endTime, long startingPrice) {
        super(id);
        this.item = item;
        this.endTime = endTime;
        this.highestBid = startingPrice;
        this.status = AuctionStatus.PENDING;
        this.observers= new ArrayList<>();
    }

    // OBSERVER PATTERN

    //Đăng ký một observer (client muốn nhận thông báo khi có bid mới).
    public void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    //Hủy đăng ký observer (client rời khỏi phòng đấu giá).
    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    /**
     * Thông báo toàn bộ observer khi có bid mới.
     * Được gọi sau mỗi lần cập nhật highestBid thành công.
     */
    public void notifyObservers(BidTransaction newBid) {
        for (AuctionObserver observer : observers) {
            observer.onBidPlaced(this, newBid);
        }
    }

    /**
     * Kiểm tra xem phiên đấu giá đã đến giờ kết thúc chưa.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }

    /**
     * Kiểm tra xem phiên còn cho phép đặt giá không.
     */
    public boolean canAcceptBids() {
        return(this.status==AuctionStatus.OPEN || (this.status == AuctionStatus.RUNNING && !isExpired()));
    }

    /**
     * Hàm gia hạn thời gian (Dùng cho tính năng Anti-sniping).
     * Ví dụ: Nếu có người đặt giá ở 30 giây cuối, cộng thêm 60 giây.
     */
    public void extendTime(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
        // Thông báo observers rằng thời gian đã thay đổi
        notifyObservers(null);
    }
    /**
     * [THÊM MỚI] Kiểm tra và tự động gia hạn nếu bid xảy ra trong ngưỡng anti-snipe.
     * Được gọi từ AuctionService sau khi xác nhận bid hợp lệ.
     */
    public void applyAntiSnipe() {
        long secondsRemaining = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
        if (secondsRemaining > 0 && secondsRemaining <= ANTI_SNIPE_THRESHOLD_SECONDS) {
            extendTime(ANTI_SNIPE_EXTENSION_SECONDS);
            System.out.println("⏱ Anti-snipe: Gia hạn thêm " + ANTI_SNIPE_EXTENSION_SECONDS
                    + "s cho phiên " + this.getId());
        }
    }
    /**
     * Chốt phiên đấu giá và cập nhật trạng thái thành FINISHED.
     */
    public void closeAuction() {
        if (this.winner != null) {
            this.status = AuctionStatus.FINISHED;
        } else {
            this.status = AuctionStatus.CANCELED; // Không có ai mua
        }
    }
    // Kiểm tra trạng thái đóng:
    public boolean isClosed() {
        return this.status == AuctionStatus.FINISHED
                || this.status == AuctionStatus.CANCELED
                || this.status == AuctionStatus.PAID;
    }
    // Kiểm tra điều kiện để chốt:
    public void determineWinner() {
        if (isExpired() && (this.status == AuctionStatus.RUNNING || this.status == AuctionStatus.OPEN)) {
            // winner đã được set qua các lần placeBid — chỉ cần chốt
            closeAuction();
        }
    }

    // GETTERS VÀ SETTERS
    public Items getItem() {
        return item;
    }
    public void setItem(Items item) {
        this.item = item;
    }

    public LocalDateTime getEndTime() {
        return endTime; }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime; }

    public AuctionStatus getStatus() {
        return status; }
    public void setStatus(AuctionStatus status) {
        this.status = status; }

    public RegularUser getWinner() {
        return winner; }
    public void setWinner(RegularUser winner) {
        this.winner = winner; }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public long getHighestBid() {
        return this.highestBid;
    }

    /**
     * Cập nhật giá cao nhất mới.
     * Lưu ý: Việc kiểm tra giá mới > giá cũ nên được thực hiện ở tầng Service/Repository
     * để đảm bảo tính nhất quán dữ liệu trước khi set vào đây.
     */
    public void setHighestBid(long highestBid) {
        this.highestBid = highestBid;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
}

package com.nhomX.example.networking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;

public class AuctionScheduler {
    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true); // Tự tắt khi Server tắt
                return t;
            });
    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }

    public void start() {
        System.out.println("SCHEDULER: Khởi động, kiểm tra " + CHECK_INTERVAL_SECONDS + "s/lần...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processAuctionStates();
            } catch (Exception e) {
                // Bắt mọi exception để tránh làm chết ScheduledExecutor
                // (ScheduledExecutorService dừng task nếu task ném unchecked exception)
                System.err.println("SCHEDULER LỖI: " + e.getMessage());
            }
        }, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void processAuctionStates() {
        LocalDateTime now = LocalDateTime.now();

        // ====================================================================
        // LUỒNG 1: MỞ CỬA PHIÊN ĐẤU GIÁ (PENDING -> OPEN) CÓ KIỂM DUYỆT
        // ====================================================================
        List<Auction> pendingAuctions =
                server.getAuctionRepository().findAuctionsByStatus(AuctionStatus.PENDING);

        if (pendingAuctions != null) {
            for (Auction auction : pendingAuctions) {
                // Điều kiện 1: Đã đến giờ mở cửa chưa?
                boolean isTimeToStart =
                        auction.getStartTime() != null && !now.isBefore(auction.getStartTime());

                // Điều kiện 2: ĐÃ ĐƯỢC ADMIN DUYỆT CHƯA? (Dựa vào cột approved_by dưới DB)
                // Model Auction của em cần bổ sung thuộc tính String approvedBy;
                boolean isAdminApproved = auction.getApprovedBy() != null
                        && !auction.getApprovedBy().trim().isEmpty();

                if (isTimeToStart && isAdminApproved) {
                    auction.setStatus(AuctionStatus.OPEN);
                    server.getAuctionRepository().updateAuctionStatus(auction);
                    System.out.println("SCHEDULER: 🟢 Đã mở cửa phiên " + auction.getId()
                            + " (Người duyệt: " + auction.getApprovedBy() + ")");

                    // (Tùy chọn) Bắn broadcast báo cho toàn Server: Có món hàng mới lên sàn!
                }
            }
        }

        // ====================================================================
        // LUỒNG 2: ĐÓNG CỬA PHIÊN ĐẤU GIÁ (OPEN / RUNNING -> FINISHED / CANCELED)
        // ====================================================================
        List<Auction> activeAuctions = server.getAuctionRepository().findExpiredOpenAuctions();

        if (activeAuctions != null) {
            for (Auction auction : activeAuctions) {
                // ĐÃ SỬA LỖI LOGIC TỬ HUYỆT BỎ DẤU "!"
                // Nếu thời gian hiện tại VẪN NHỎ HƠN thời gian kết thúc -> Chưa hết giờ -> Bỏ qua
                if (auction.getEndTime() == null || now.isBefore(auction.getEndTime())) {
                    continue;
                }

                // ĐÃ HẾT GIỜ -> Chuyển trạng thái
                // Lưu ý: Trong Model Auction, hàm closeAuction() của em nên tự động xét:
                // Nếu highestBid > 0 (có người mua) -> Trạng thái FINISHED
                // Nếu highestBid == 0 -> Trạng thái CANCELED
                auction.closeAuction();

                boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);

                if (saved) {
                    String winnerId =
                            (auction.getWinner() != null) ? auction.getWinner().getId() : null;

                    // Thông báo realtime tới mọi Client đang trong phòng
                    server.broadcastToAuction(auction.getId(),
                            Message.auctionClosed(auction.getId(), winnerId));

                    System.out.println("SCHEDULER: 🔴 Đã đóng phiên " + auction.getId()
                            + " | Winner: " + (winnerId != null ? winnerId : "Không có"));
                }
            }
        }
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("SCHEDULER: Đã tắt.");
        }
    }
}

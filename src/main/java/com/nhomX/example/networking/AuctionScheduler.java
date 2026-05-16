package com.nhomX.example.networking;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.BidRepository;

 import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {
    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true);  // Tự tắt khi Server tắt
                return t;
            });

    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }

    public void start() {
        System.out.println("SCHEDULER: Khởi động, kiểm tra "
                + CHECK_INTERVAL_SECONDS + "s/lần...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCloseExpiredAuctions();
            } catch (Exception e) {
                // Bắt mọi exception để tránh làm chết ScheduledExecutor
                // (ScheduledExecutorService dừng task nếu task ném unchecked exception)
                System.err.println("SCHEDULER LỖI: " + e.getMessage());
            }
        }, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void checkAndCloseExpiredAuctions() {
        List<Auction> runningAuctions =
                server.getAuctionRepository().findAuctionsByStatus(AuctionStatus.RUNNING);

        if (runningAuctions == null || runningAuctions.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (Auction auction : runningAuctions) {
            if (auction.getEndTime() == null || !now.isBefore(auction.getEndTime())) {
                continue; // Chưa hết giờ
            }

            // Đóng phiên: FINISHED nếu có winner, CANCELED nếu không ai bid
            auction.closeAuction();
            boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);

            if (saved) {
                String winnerId = (auction.getWinner() != null)
                        ? auction.getWinner().getId() : null;

                // Thông báo realtime tới mọi Client đang xem phiên này
                server.broadcastToAuction(
                        auction.getId(),
                        Message.auctionClosed(auction.getId(), winnerId));

                System.out.println("SCHEDULER: Đã đóng phiên " + auction.getId()
                        + " | Winner: " + (winnerId != null ? winnerId : "Không có"));
            } else {
                System.err.println("SCHEDULER: Không thể lưu trạng thái đóng cho phiên "
                        + auction.getId());
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
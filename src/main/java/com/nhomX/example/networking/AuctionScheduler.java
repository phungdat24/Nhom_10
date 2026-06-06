package com.nhomX.example.networking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.User;

public class AuctionScheduler {
    private static final Logger logger = LoggerFactory.getLogger(AuctionScheduler.class);
    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true);
                return t;
            });
    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }

    public void start() {
        logger.info("SCHEDULER: Khởi động, kiểm tra mỗi {} giây.", CHECK_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processAuctionStates();
            } catch (Exception e) {
                logger.error("SCHEDULER LỖI", e);
            }
        }, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void processAuctionStates() {
        LocalDateTime now = LocalDateTime.now();

        List<Auction> readyAuctions = server.getAuctionRepository().findReadyToOpenAuctions();

        if (readyAuctions != null && !readyAuctions.isEmpty()) {
            for (Auction auction : readyAuctions) {
                auction.setStatus(AuctionStatus.OPEN);
                boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);
                if (saved) {
                    logger.info("SCHEDULER: Đã mở phiên {}", auction.getId());
                }
            }
        }

        List<Auction> activeAuctions = server.getAuctionRepository().findExpiredOpenAuctions();

        if (activeAuctions != null) {
            for (Auction auction : activeAuctions) {
                if (auction.getEndTime() == null || now.isBefore(auction.getEndTime())) {
                    continue;
                }

                String winnerId =
                        (auction.getWinner() != null) ? auction.getWinner().getId() : null;

                // Goi ham tat toan: cong tien cho seller va chuyen status PAID/CANCELED.
                boolean isSettled =
                        server.getAuctionRepository().settleAuctionPayment(auction.getId());

                if (isSettled) {
                    server.broadcastToAuction(auction.getId(),
                            Message.auctionClosed(auction.getId(), winnerId));

                    // QUAN TRONG: gui rieng cho seller de UI cap nhat so du tuc thi.
                    String sellerId = getSellerId(auction);
                    if (winnerId != null && sellerId != null) {
                        User sellerDb = server.getUserRepository().findById(sellerId);
                        if (sellerDb != null) {
                            server.sendToUser(sellerId, new Message("DEPOSIT_RESULT",
                                    new Object[] {true, sellerDb.getBalance()}));
                        }
                    }

                    logger.info("SCHEDULER: Đã đóng phiên {} | Winner: {}", auction.getId(),
                            winnerId != null ? winnerId : "Không có");
                }
            }
        }
    }

    private String getSellerId(Auction auction) {
        if (auction.getItem() == null || auction.getItem().getSeller() == null) {
            return null;
        }
        return auction.getItem().getSeller().getId();
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            logger.info("SCHEDULER: Đã tắt.");
        }
    }
}

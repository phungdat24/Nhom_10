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
                t.setDaemon(true);
                return t;
            });
    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }

    public void start() {
        System.out.println("SCHEDULER: Khoi dong, kiem tra "
                + CHECK_INTERVAL_SECONDS + "s/lan...");
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                processAuctionStates();
            } catch (Exception e) {
                System.err.println("SCHEDULER LOI: " + e.getMessage());
            }
        }, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void processAuctionStates() {
        LocalDateTime now = LocalDateTime.now();

        List<Auction> readyAuctions =
                server.getAuctionRepository().findReadyToOpenAuctions();

        if (readyAuctions != null && !readyAuctions.isEmpty()) {
            for (Auction auction : readyAuctions) {
                auction.setStatus(AuctionStatus.OPEN);
                boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);
                if (saved) {
                    System.out.println("SCHEDULER: Da mo cua phien " + auction.getId());
                }
            }
        }

        List<Auction> activeAuctions = server.getAuctionRepository().findExpiredOpenAuctions();

        if (activeAuctions != null) {
            for (Auction auction : activeAuctions) {
                auction.closeAuction();
                boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);
                if (saved) {
                    String winnerId =
                            (auction.getWinner() != null) ? auction.getWinner().getId() : null;
                    server.broadcastToAuction(auction.getId(),
                            Message.auctionClosed(auction.getId(), winnerId));

                    System.out.println("SCHEDULER: Da dong phien " + auction.getId()
                            + " | Winner: " + (winnerId != null ? winnerId : "Khong co"));
                }
            }
        }
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("SCHEDULER: Da tat.");
        }
    }
}

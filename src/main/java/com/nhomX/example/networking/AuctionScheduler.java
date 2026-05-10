package com.nhomX.example.networking;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }

    public void start() {
        System.out.println("SCHEDULER: Đã khởi động luồng chạy ngầm kiểm tra Database (5s/lần)...");
        
        // Cú pháp: scheduleAtFixedRate(task, initialDelay, period, TimeUnit)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCloseAuctions();
            } catch (Exception e) {
                System.err.println("SCHEDULER LỖI: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS); 
    }

    /**
     * Hàm này chứa logic giao tiếp với Database để tìm món hàng hết hạn
     */
    private void checkAndCloseAuctions() {
       
    }

    // Hàm dùng để tắt luồng ngầm an toàn khi Server tắt
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("SCHEDULER: Đã tắt luồng bảo vệ.");
        }
    }
}
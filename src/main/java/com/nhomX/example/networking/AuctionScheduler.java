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
        System.out.println("SCHEDULER: Đã khởi động luồng chạy ngầm kiểm tra Database ...");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCloseAuctions();
            } catch (Exception e) {
                System.err.println("SCHEDULER LỖI: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS); 
    }
     private void checkAndCloseAuctions() {
     }
}
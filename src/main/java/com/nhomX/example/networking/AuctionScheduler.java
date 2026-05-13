package com.nhomX.example.networking;

import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.BidRepository;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AuctionServer server;
    private final AuctionRepository auctionRepo;
    private final BidRepository bidRepo;

    public AuctionScheduler(AuctionServer server, AuctionRepository auctionRepo, BidRepository bidRepo) {
        this.server = server;
        this.auctionRepo = auctionRepo;
        this.bidRepo = bidRepo;
    }

    public void start() {
        System.out.println("⏰ [SCHEDULER] Hệ thống quét phiên hết hạn đã chạy (5 giây/lần)...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndCloseAuctions();
            } catch (Exception e) {
                System.err.println("❌ [SCHEDULER ERROR]: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void checkAndCloseAuctions() {
        // 1. Gọi đúng hàm bạn vừa thêm: lấy các phiên hết giờ nhưng vẫn OPEN
        List<Auction> expiredList = auctionRepo.findExpiredOpenAuctions();

        for (Auction auction : expiredList) {
            System.out.println("⚡ [PROCESS] Chốt phiên: " + auction.getId());

            // 2. Tìm lượt trả giá cao nhất từ BidRepository
            BidTransaction highestBid = bidRepo.getHighestBid(auction.getId());

            String winnerName = "Không có";
            String winnerId = null;
            long finalPrice = auction.getHighestBid(); // Mặc định lấy giá hiện tại

            if (highestBid != null) {
                // Lưu ý: Code của bạn dùng getBidder() trả về RegularUser
                winnerName = highestBid.getBidder().getUserName();
                winnerId = highestBid.getBidder().getId();
                finalPrice = highestBid.getAmount();

                // 3. Cập nhật người thắng vào Database (Dùng đúng tên hàm của bạn)
                auctionRepo.updateHighestBidAndWinner(auction.getId(), finalPrice, winnerId);
            }

            // 4. Chốt trạng thái phiên thành FINISHED (Sử dụng Enum AuctionStatus)
            auctionRepo.updateStatus(auction.getId(), AuctionStatus.FINISHED);

            // 5. Gửi thông báo Real-time cho những người đang xem phiên này
            Message closeMsg = Message.auctionClosed(auction.getId(), winnerName, finalPrice);
            server.broadcastToAuction(auction.getId(), closeMsg);

            System.out.println("✅ [DONE] Đã đóng ID: " + auction.getId() + ". Thắng: " + winnerName);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        System.out.println("🛑 [SCHEDULER] Đã dừng.");
    }
}
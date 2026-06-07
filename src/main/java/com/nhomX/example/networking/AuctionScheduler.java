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
    // Chu kì quét
    private static final int CHECK_INTERVAL_SECONDS = 1;
    // Khởi tạo một bộ lập lich đa luồng với 1 luồng duy nhất
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2,r -> {
                Thread t = new Thread(r, "auction-scheduler");
                //Luồng Daemon là loại luồng chạy ngầm
                //Khi toàn bộ chương trình chính (Main thread) tắt, JVM sẽ tự động bóp cổ luồng Daemon này tắt theo.
                // Nếu để mặc định (User Thread), ứng dụng sẽ không bao giờ tắt được dù đã bấm nút X, vì luồng này vẫn cứ chạy mãi mãi!
                t.setDaemon(true);
                return t;
            });
    //Biến tham chiếu (Reference) tới đối tượng Server chính.
    // Scheduler cần cầm cái này để nhờ Server thực hiện gửi tin nhắn mạng (Broadcast)
    private final AuctionServer server;

    public AuctionScheduler(AuctionServer server) {
        this.server = server;
    }
    // Hàm công khai dùng để kích hoạt bộ hẹn giờ chạy.
    public void start() {
        logger.info("SCHEDULER: Khởi động, kiểm tra mỗi {} giây.", CHECK_INTERVAL_SECONDS);
        // FixedRate: Nó cố gắng chạy ĐÚNG 5 giây một lần, bất kể việc xử lý dữ liệu bên trong mất bao lâu.
        // Đảm bảo tính đúng giờ cho phiên đấu giá
        // FixedDelay: Nó chờ xử lý xong, nghỉ 5 giây rồi mới chạy tiếp.
        // Nếu xử lý mất 3 giây, chu kỳ thực tế sẽ thành 8 giây. Rất dễ gây sai lệch thời gian kết thúc đấu giá.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processAuctionStates();
            } catch (Exception e) {
                logger.error("SCHEDULER LỖI", e);
            }
        }, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS); // 0 chạy ngay lập tức khi go hàm
        // CHECK_INTERVAL_SECONDS = Chu kỳ lặp. TimeUnit.SECONDS = Đơn vị thời gian là giây

        // NHIỆM VỤ 2: [THÊM MỚI] BẮN THỜI GIAN SERVER_TICK (Chạy 1 giây/lần)
        // ==========================================================
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Lấy thời gian chuẩn xác nhất từ hệ thống Server lúc này
                String currentServerTime = LocalDateTime.now().toString();

                // 2. Phát thanh cho TOÀN BỘ Client (cả Admin lẫn User thường) để đồng bộ đồng hồ
                // Dùng broadcastToAll vì màn hình đấu giá của user thường cũng cần đếm ngược
                server.broadcastToAll(new Message("SERVER_TICK", currentServerTime));

            } catch (Exception e) {
                logger.error("SCHEDULER LỖI KHI BẮN NHỊP TIM", e);
            }
        }, 0, 1, TimeUnit.SECONDS); // Chạy ngay lập tức (0), lặp lại mỗi (1) giây
    }

    private void processAuctionStates() {
        LocalDateTime now = LocalDateTime.now();
        // Gọi xuống DB lấy những phiên đang ở trạng thái UP_COMING và có giờ mở bán <= giờ hiện tại.
        List<Auction> readyAuctions = server.getAuctionRepository().findReadyToOpenAuctions();

        if (readyAuctions != null && !readyAuctions.isEmpty()) {
            for (Auction auction : readyAuctions) {
                // Cập nhật status sang OPEN nếu đên thời gian
                auction.setStatus(AuctionStatus.OPEN);
                // Lưu trạng thái mới đó xuống cơ sở dữ liệu để ghi nhận chính thức.
                boolean saved = server.getAuctionRepository().updateAuctionStatus(auction);
                if (saved) {
                    logger.info("SCHEDULER: Đã mở phiên {}", auction.getId());
                }
            }
        }
        // Lấy những phiên quá hạn:
        List<Auction> activeAuctions = server.getAuctionRepository().findExpiredOpenAuctions();

        if (activeAuctions != null) {
            for (Auction auction : activeAuctions) {
                // kiểm tra những phiên chưa hết gio thì bỏ qua
                if (auction.getEndTime() == null || now.isBefore(auction.getEndTime())) {
                    continue;
                }
                // Toán tử ba ngôi
                // Nếu không có ai thắng thì null
                String winnerId =
                        (auction.getWinner() != null) ? auction.getWinner().getId() : null;

                // Gọi một Transaction dưới DB. Hàm này sẽ làm hàng loạt việc: Đổi trạng thái sang FINISHED (hoặc CANCELED nếu ế)
                // Đóng băng giá trị trúng thầu, trừ tiền người mua, cộng tiền người bán.
                boolean isSettled =
                        server.getAuctionRepository().settleAuctionPayment(auction.getId());

                if (isSettled) {
                    //Nếu DB hoàn tất giao dịch tài chính, ra lệnh cho Server kích hoạt ống loa (broadcast) báo hiệu AUCTION_CLOSED gửi xuống toàn bộ các Client đang mở tab xem món này để khóa nút đặt giá.
                    server.broadcastToAuction(auction.getId(),
                            Message.auctionClosed(auction.getId(), winnerId));

                    // QUAN TRONG: gui rieng cho seller de UI cap nhat so du tuc thi.
                    String sellerId = getSellerId(auction);
                    // Nếu có người mua thì mới cộng tiền
                    if (winnerId != null && sellerId != null) {
                        // Lôi tài khoản người bán lên sau khi cap nhật số dư
                        User sellerDb = server.getUserRepository().findById(sellerId);
                        if (sellerDb != null) {
                            /*
                            * hệ thống mượn lệnh DEPOSIT_RESULT để bí mật đẩy thẳng số dư mới xuyên qua Socket
                            * Giao diện (UI) bên Client bắt được DEPOSIT_RESULT sẽ tự động nháy số dư tài khoản tăng lên thời gian thực!
                            */
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
    // Trích xuất id người bán
    private String getSellerId(Auction auction) {
        // Kiểm tra null
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

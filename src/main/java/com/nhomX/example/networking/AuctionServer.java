package com.nhomX.example.networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.AutoBidRepository;
import com.nhomX.example.repository.AutoBidRepositoryImpl;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.BidRepositoryImpl;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;

public class AuctionServer {
    private static final Logger logger = LoggerFactory.getLogger(AuctionServer.class);
    private static final int PORT = 8080;
    private final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Danh sách toàn bộ Client đang kết nối (dùng cho các thông báo hệ thống nếu cần)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // THÊM MỚI: Map quản lý người xem theo từng itemId
    // Key: itemId, Value: Tập hợp (Set) các ClientHandler đang xem món đó
    // Dùng newKeySet() để tạo ra một Set an toàn (Thread-safe Set) dựa trên ConcurrentHashMap
    private final ConcurrentHashMap<String, Set<ClientHandler>> auctionViewers =
            new ConcurrentHashMap<>();

    // Khởi tạo Repository dùng chung tại cấp độ Server
    private final ItemRepository itemRepository = new ItemRepositoryImpl();
    private final UserRepository userRepository = new UserRepositoryImpl();
    private final BidRepository bidRepository = new BidRepositoryImpl();
    private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();
    private final AutoBidRepository autoBidRepository = new AutoBidRepositoryImpl();


    private AuctionScheduler auctionScheduler;

    public void start() {
        // Đăng ký Shutdown Hook. Khi tắt app đoạn code này sẽ chạy để đóng sạch luồng.
        registerShutdownHook();

        // BUG FIX: Scheduler được tạo nhưng start() không bao giờ được gọi trong code gốc
        // → phiên đấu giá hết hạn không bao giờ tự đóng
        AuctionScheduler scheduler = new AuctionScheduler(this);
        scheduler.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("SERVER: Đang đợi kết nối tại cổng {}...", PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                logger.info("SERVER: Có kết nối mới từ {}", socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this, itemRepository,
                        userRepository, bidRepository, auctionRepository, autoBidRepository);
                clients.add(handler);
                serverExecutor.execute(handler);
                // [THÊM MỚI] Báo cho tất cả client biết có người mới vào
                broadcastOnlineCount();
            }
        } catch (IOException e) {
            logger.error("SERVER ERROR: {}", e.getMessage(), e);
        } finally {
            // Đề phòng trường hợp vòng lặp văng lỗi, chặn luôn luồng ở đây
            if (!serverExecutor.isShutdown()) {
                serverExecutor.shutdown();
            }
        }
    }

    /** Đăng ký hook dọn dẹp khi JVM tắt (Ctrl+C hoặc kill). */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("SERVER: Đang dọn dẹp trước khi tắt...");
            if (!serverExecutor.isShutdown()) {
                serverExecutor.shutdown();
            }
        }, "shutdown-hook"));
    }

    // Client gọi hàm này khi bấm vào xem chi tiết một món hàng
    public void watchAuction(String auctionId, ClientHandler client) {
        // NẾU ITEM ID BỊ NULL, BỎ QUA LUÔN, KHÔNG ĐƯA VÀO HASHMAP
        if (auctionId == null || auctionId.isEmpty()) {
            logger.warn("SERVER LỖI: Client yêu cầu xem một Item không có ID!");
            return;
        }
        // Đảm bảo thao tác thêm người xem không bị đụng độ luồng
        // Nếu itemId chưa ai xem thì tạo danh sách mới, sau đó thêm client vào
        auctionViewers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(client);
        logger.info("SERVER: Một client vừa tham gia xem món {}", auctionId);
    }

    // Client gọi hàm này khi thoát khỏi trang chi tiết món hàng
    public void unwatchAuction(String auctionId, ClientHandler client) {
        Set<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            viewers.remove(client);
            if (viewers.isEmpty()) {
                // Dọn dẹp bộ nhớ nếu không còn ai xem
                auctionViewers.remove(auctionId);
            }
        }
    }

    /**
     * Broadcast message tới tất cả Client đang xem một phiên cụ thể. Đây là trung tâm của Realtime
     * Update.
     *
     * [FIX] Đổi tên từ broadcastToItem → broadcastToAuction cho đúng ngữ nghĩa.
     */
    public void broadcastToAuction(String auctionId, Message msg) {
        Set<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                // Nếu 1 client bị lỗi ngắt kết nối, lỗi sẽ bị bắt gọn bên trong hàm sendToClient
                // Vòng lặp vẫn an toàn chạy tiếp cho những người dùng khác.
                client.sendToClient(msg);
            }
        }
    }

    /**
     * Broadcast tới toàn bộ client đang kết nối. Dùng cho thông báo hệ thống (VD: Server sắp tắt,
     * phiên mới được duyệt...).
     */
    public void broadcastToAll(Message msg) {
        for (ClientHandler client : clients) {
            client.sendToClient(msg);
        }
    }

    /**
     * Broadcast tới tất cả Client đang đăng nhập với role ADMIN. Dùng để thông báo sản phẩm mới chờ
     * duyệt ngay lập tức.
     */
    public void broadcastToAdmins(Message msg) {
        for (ClientHandler client : clients) {
            if (client.isAdminClient()) {
                client.sendToClient(msg);
            }
        }
    }

    /**
     * Gửi message đích danh tới một user cụ thể (theo userId). Dùng cho: hoàn tiền khi bị vượt giá,
     * thông báo cá nhân.
     *
     * @param userId ID của user cần nhận tin
     * @param msg Message cần gửi
     */
    public void sendToUser(String userId, Message msg) {
        for (ClientHandler client : clients) {
            User user = client.getCurrentUser();
            if (user != null && userId.equals(user.getId())) {
                client.sendToClient(msg);
                return; // Tìm thấy rồi, dừng vòng lặp
            }
        }
        // Không tìm thấy = user đang offline — bỏ qua, không báo lỗi
        logger.info("SERVER: User {} không online, bỏ qua sendToUser.", userId);
    }

    // ĐÃ SỬA: Dọn dẹp triệt để khi Client ngắt kết nối (tắt app)
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        // Xóa client này khỏi tất cả các danh sách món hàng đang xem
        for (Set<ClientHandler> viewers : auctionViewers.values()) {
            viewers.remove(client);
        }
        // [THÊM MỚI] Báo cho client biết có người vừa thoát
        broadcastOnlineCount();
    }

    public AuctionRepository getAuctionRepository() {
        return auctionRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Trả về số lượng Client (User) đang kết nối trực tiếp tới Server
     */
    public int getOnlineUserCount() {
        return clients.size();
    }

    public static void main(String[] args) {
        new AuctionServer().start();
        logger.info("Đang khởi động Server...");

    }

    /**
     * THÊM MỚI: Bắn gói tin cập nhật số người online tới tất cả Client
     */
    public void broadcastOnlineCount() {
        int currentOnline = clients.size();
        Message msg = new Message("UPDATE_ONLINE_COUNT", currentOnline);
        broadcastToAll(msg); // Hàm này em đã viết sẵn rất chuẩn rồi
    }
}

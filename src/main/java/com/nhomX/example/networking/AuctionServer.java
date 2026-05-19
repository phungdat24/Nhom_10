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
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.AuctionRepositoryImpl;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.BidRepositoryImpl;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.repository.UserRepositoryImpl;

public class AuctionServer {
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

    private AuctionScheduler auctionScheduler;

    public void start() {
        // Đăng ký Shutdown Hook. Khi tắt app đoạn code này sẽ chạy để đóng sạch luồng.
        registerShutdownHook();

        // BUG FIX: Scheduler được tạo nhưng start() không bao giờ được gọi trong code gốc
        // → phiên đấu giá hết hạn không bao giờ tự đóng
        AuctionScheduler scheduler = new AuctionScheduler(this);
        scheduler.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SERVER: Đang đợi kết nối tại cổng " + PORT + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("SERVER: Có kết nối mới từ " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this, itemRepository,
                        userRepository, bidRepository, auctionRepository);
                clients.add(handler);
                serverExecutor.execute(handler);
                // [THÊM MỚI] Báo cho tất cả client biết có người mới vào
                broadcastOnlineCount();
            }
        } catch (IOException e) {
            System.err.println("SERVER ERROR: " + e.getMessage());
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
            System.out.println("SERVER: Đang dọn dẹp trước khi tắt...");
            if (!serverExecutor.isShutdown()) {
                serverExecutor.shutdown();
            }
        }, "shutdown-hook"));
    }

    // Client gọi hàm này khi bấm vào xem chi tiết một món hàng
    public void watchAuction(String auctionId, ClientHandler client) {
        // NẾU ITEM ID BỊ NULL, BỎ QUA LUÔN, KHÔNG ĐƯA VÀO HASHMAP
        if (auctionId == null || auctionId.isEmpty()) {
            System.err.println("SERVER LỖI: Client yêu cầu xem một Item không có ID!");
            return;
        }
        // Đảm bảo thao tác thêm người xem không bị đụng độ luồng
        // Nếu itemId chưa ai xem thì tạo danh sách mới, sau đó thêm client vào
        auctionViewers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(client);
        System.out.println("SERVER: Một client vừa tham gia xem món " + auctionId);
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

    /**
     * Trả về số lượng Client (User) đang kết nối trực tiếp tới Server
     */
    public int getOnlineUserCount() {
        return clients.size();
    }

    public static void main(String[] args) {
        new AuctionServer().start();
        System.out.println("Đang khởi động Server...");

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

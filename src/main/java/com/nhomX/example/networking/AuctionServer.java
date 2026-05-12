package com.nhomX.example.networking;

import com.nhomX.example.repository.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {
    private static final int PORT = 8080;
    private final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Danh sách toàn bộ Client đang kết nối (dùng cho các thông báo hệ thống nếu cần)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // THÊM MỚI: Map quản lý người xem theo từng itemId
    // Key: itemId, Value: Tập hợp (Set) các ClientHandler đang xem món đó
    private final ConcurrentHashMap<String, Set<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    //Khởi tạo Repository dùng chung tại cấp độ Server
    private final ItemRepository itemRepository = new ItemRepositoryImpl();
    private final UserRepository userRepository = new UserRepositoryImpl();
    private final BidRepository bidRepository = new BidRepositoryImpl();
    private final AuctionRepository auctionRepository = new AuctionRepositoryImpl();

    public void start() {
        // Đăng ký Shutdown Hook. Khi tắt app đoạn code này sẽ chạy để đóng sạch luồng.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("SERVER: Đang tiến hành dọn dẹp và tắt ExecutorService...");
            serverExecutor.shutdown();
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SERVER: Đang đợi kết nối tại cổng " + PORT + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("SERVER: Có kết nối mới từ " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this, itemRepository, userRepository, bidRepository, auctionRepository);
                clients.add(handler);
                serverExecutor.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("SERVER ERROR: " + e.getMessage());
        }finally {
            // Đề phòng trường hợp vòng lặp văng lỗi, chặn luôn luồng ở đây
            if (!serverExecutor.isShutdown()) {
                serverExecutor.shutdown();
            }
        }
    }

    // Client gọi hàm này khi bấm vào xem chi tiết một món hàng
    public void watchAuction(String auctionId, ClientHandler client) {
        // NẾU ITEM ID BỊ NULL, BỎ QUA LUÔN, KHÔNG ĐƯA VÀO HASHMAP
        if (auctionId== null || auctionId.isEmpty()) {
            System.err.println("SERVER LỖI: Client yêu cầu xem một Item không có ID!");
            return;
        }
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
     * Broadcast message tới tất cả Client đang xem một phiên cụ thể.
     * Đây là trung tâm của Realtime Update.
     *
     * [FIX] Đổi tên từ broadcastToItem → broadcastToAuction cho đúng ngữ nghĩa.
     */
    public void broadcastToAuction(String auctionId, Message msg) {
        Set<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                client.sendToClient(msg);
            }
        }
    }
    /**
     *  Broadcast tới toàn bộ client đang kết nối.
     * Dùng cho thông báo hệ thống (VD: Server sắp tắt, phiên mới được duyệt...).
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
    }

    public static void main(String[] args) {
        AuctionServer server = new AuctionServer();
        System.out.println("Đang khởi động Server...");
        server.start();
    }
}

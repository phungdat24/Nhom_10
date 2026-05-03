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

public class AuctionServer {
    private static final int PORT = 8080;
    private final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Danh sách toàn bộ Client đang kết nối (dùng cho các thông báo hệ thống nếu cần)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // THÊM MỚI: Map quản lý người xem theo từng itemId
    // Key: itemId, Value: Tập hợp (Set) các ClientHandler đang xem món đó
    private final ConcurrentHashMap<String, Set<ClientHandler>> itemViewers =
            new ConcurrentHashMap<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SERVER: Đang đợi kết nối tại cổng " + PORT + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("SERVER: Có kết nối mới từ " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                serverExecutor.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("SERVER ERROR: " + e.getMessage());
        }
    }

    // THÊM MỚI: Client gọi hàm này khi bấm vào xem chi tiết một món hàng
    public void watchItem(String itemId, ClientHandler client) {
        // NẾU ITEM ID BỊ NULL, BỎ QUA LUÔN, KHÔNG ĐƯA VÀO HASHMAP
        if (itemId == null || itemId.isEmpty()) {
            System.err.println("SERVER LỖI: Client yêu cầu xem một Item không có ID!");
            return;
        }
        // Nếu itemId chưa ai xem thì tạo danh sách mới, sau đó thêm client vào
        itemViewers.computeIfAbsent(itemId, k -> ConcurrentHashMap.newKeySet()).add(client);
        System.out.println("SERVER: Một client vừa tham gia xem món " + itemId);
    }

    // THÊM MỚI: Client gọi hàm này khi thoát khỏi trang chi tiết món hàng
    public void unwatchItem(String itemId, ClientHandler client) {
        Set<ClientHandler> viewers = itemViewers.get(itemId);
        if (viewers != null) {
            viewers.remove(client);
            if (viewers.isEmpty()) {
                itemViewers.remove(itemId); // Dọn dẹp bộ nhớ nếu không còn ai xem
            }
        }
    }

    // ĐÃ SỬA: Gửi tin nhắn cho những ai ĐANG XEM itemId cụ thể
    public void broadcastToItem(String itemId, Message msg) {
        Set<ClientHandler> viewers = itemViewers.get(itemId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                client.sendToClient(msg);
            }
        }
    }

    // ĐÃ SỬA: Dọn dẹp triệt để khi Client ngắt kết nối (tắt app)
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        // Xóa client này khỏi tất cả các danh sách món hàng đang xem
        for (Set<ClientHandler> viewers : itemViewers.values()) {
            viewers.remove(client);
        }
    }

    public static void main(String[] args) {
        AuctionServer server = new AuctionServer();
        System.out.println("Đang khởi động Server...");
        server.start();
    }
}

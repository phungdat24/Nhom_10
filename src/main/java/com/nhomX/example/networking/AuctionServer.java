package com.nhomX.example.networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {
    private static final int PORT = 8080;

    // serverExecutor: Quản lý các luồng ảo (Virtual Threads).
    // Giúp server xử lý hàng ngàn kết nối cùng lúc mà không tốn nhiều RAM.
    private final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Danh sách các Client đang kết nối
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SERVER: Đang đợi kết nối tại cổng " + PORT + "...");

            while (true) {
                Socket socket = serverSocket.accept(); // Chấp nhận một client mới
                System.out.println("SERVER: Có kết nối mới từ " + socket.getInetAddress());

                // Tạo một người quản lý riêng cho client này
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);

                // Executor thực thi handler này trong một luồng riêng
                serverExecutor.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("SERVER ERROR: " + e.getMessage());
        }
    }

    /**
     * Gửi tin nhắn tới TẤT CẢ các client (Cơ chế Broadcast - Observer Pattern)
     */
    public void broadcast(Message msg) {
        for (ClientHandler client : clients) {
            client.sendToClient(msg);
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}
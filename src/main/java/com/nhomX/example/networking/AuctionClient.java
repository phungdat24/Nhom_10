package com.nhomX.example.networking;

import java.io.*;
import java.net.Socket;

public class AuctionClient {
    private String username;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public AuctionClient(String username) {
        this.username = username;
    }

    public void connect(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // LUỒNG NGẦM: Luôn lắng nghe cập nhật từ Server để không làm treo UI
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true); // Tự tắt khi ứng dụng chính tắt
            listenerThread.start();

        } catch (IOException e) {
            System.err.println("CLIENT: Không thể kết nối tới Server.");
        }
    }

    // Gửi yêu cầu đặt giá lên Server
    public void placeBid(String itemId, double price) {
        try {
            Message bid = new Message("BID", username, itemId, price);
            out.writeObject(bid);
            out.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Lắng nghe các UPDATE từ Server gửi về (Realtime)
    private void listenToServer() {
        try {
            Message msgFromServer;
            while ((msgFromServer = (Message) in.readObject()) != null) {
                if ("UPDATE".equals(msgFromServer.getType())) {
                    System.out.println("\n[THÔNG BÁO MỚI]: " + msgFromServer.getUsername() +
                            " đã đặt giá $" + msgFromServer.getAmount() +
                            " cho " + msgFromServer.getItemId());
                }
            }
        } catch (Exception e) {
            System.out.println("CLIENT: Mất kết nối với Server.");
        }
    }
}
package com.nhomX.example.networking;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo luồng vào/ra dữ liệu
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Message msgFromClient;
            // Vòng lặp liên tục lắng nghe tin nhắn từ Client này gửi lên
            while ((msgFromClient = (Message) in.readObject()) != null) {
                System.out.println("SERVER NHẬN: " + msgFromClient);

                if ("BID".equals(msgFromClient.getType())) {
                    // Xử lý logic đặt giá... (nếu thành công thì thông báo cho tất cả)
                    Message update = new Message("UPDATE", msgFromClient.getUsername(),
                            msgFromClient.getItemId(), msgFromClient.getAmount());
                    server.broadcast(update);
                }
            }
        } catch (Exception e) {
            System.out.println("SERVER: Client ngắt kết nối.");
        } finally {
            cleanup();
        }
    }

    // Gửi tin nhắn ngược về máy của Client
    public void sendToClient(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            cleanup();
        }
    }

    private void cleanup() {
        server.removeClient(this);
        try {
            socket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
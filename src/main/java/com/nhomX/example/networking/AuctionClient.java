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
                String msgType = msgFromServer.getType();

                if ("UPDATE".equals(msgType)) {
                    // Code cũ giữ nguyên
                    System.out.println("\n[THÔNG BÁO MỚI]: " + msgFromServer.getUsername() +
                            " đã đặt giá $" + msgFromServer.getAmount() +
                            " cho " + msgFromServer.getItemId());
                }
                // THÊM MỚI Ở ĐÂY: Xử lý Đăng nhập thành công
                else if ("LOGIN_SUCCESS".equals(msgType)) {
                    // Dùng Platform.runLater để giao diện JavaFX không bị sập khi cập nhật từ luồng ngầm
                    javafx.application.Platform.runLater(() -> {
                        // TODO: Chuyển sang màn hình Dashboard
                        System.out.println("Giao diện: Đăng nhập thành công!");
                    });
                }
                // THÊM MỚI: Xử lý Đăng nhập thất bại
                else if ("LOGIN_FAIL".equals(msgType)) {
                    javafx.application.Platform.runLater(() -> {
                        // Giả sử đã có class AlertUtils
                        // AlertUtils.showError("Lỗi", "Sai thông tin đăng nhập!");
                        System.out.println("Giao diện: Đăng nhập thất bại!");
                    });
                }
                // Tương tự, em có thể thêm else if ("REGISTER_SUCCESS") vào đây
            }
        } catch (Exception e) {
            System.out.println("CLIENT: Mất kết nối với Server.");
        }
    }
    // Hàm mới: Gửi bất kỳ Message nào lên Server (dùng cho Login, Register...)
    public void sendToServer(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            } else {
                System.err.println("Lỗi: Chưa kết nối tới Server!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
package com.nhomX.example.networking;

import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.ItemRepositoryImpl;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    // Gọi kho chứa dữ liệu ra để sẵn sàng làm việc
    private ItemRepository itemRepository = new ItemRepositoryImpl();

    private com.nhomX.example.repository.UserRepository userRepository = new com.nhomX.example.repository.UserRepositoryImpl();

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
                    server.broadcastToItem(msgFromClient.getItemId(), update);
                }
                // Khi Client click vào xem một sản phẩm
                else if ("WATCH".equals(msgFromClient.getType())) {
                    server.watchItem(msgFromClient.getItemId(), this);
                }
                // Khi Client quay lại màn hình chính hoặc xem sản phẩm khác
                else if ("UNWATCH".equals(msgFromClient.getType())) {
                    server.unwatchItem(msgFromClient.getItemId(), this);
                }
                else if ("LOGIN".equals(msgFromClient.getType())) {
                    // 1. Mở gói hàng lấy dữ liệu Client gửi
                    String[] data = (String[]) msgFromClient.getData();
                    String email = data[0];
                    String pass = data[1];

                    // 2. Chọc xuống Database kiểm tra
                    com.nhomX.example.model.User loggedInUser = userRepository.login(email, pass);

                    // 3. Nói thầm kết quả lại cho ĐÚNG Client này
                    if (loggedInUser != null) {
                        this.sendToClient(new Message("LOGIN_SUCCESS", loggedInUser));
                    } else {
                        this.sendToClient(new Message("LOGIN_FAIL", null));
                    }
                }
                else if ("REGISTER".equals(msgFromClient.getType())) {
                    Object[] data = (Object[]) msgFromClient.getData();
                    String email = (String) data[0];
                    String pass = (String) data[1];
                    String name = (String) data[2];
                    double balance = (Double) data[3];

                    String newId = java.util.UUID.randomUUID().toString();
                    com.nhomX.example.model.User newUser = new com.nhomX.example.model.User(newId, email, pass, name, balance);

                    boolean isSuccess = userRepository.register(newUser);

                    if (isSuccess) {
                        this.sendToClient(new Message("REGISTER_SUCCESS", "Tạo tài khoản thành công!"));
                    } else {
                        this.sendToClient(new Message("REGISTER_FAIL", "Email đã tồn tại!"));
                    }
                }else if ("GET_ALL_ITEMS".equals(msgFromClient.getType())) {
                    // Server chọc vào DB lấy 10 item
                    java.util.List<com.nhomX.example.model.Items> itemList = itemRepository.findAll();

                    // Đóng gói danh sách gửi trả lại ĐÚNG cái Client vừa xin
                    Message response = new Message("RETURN_ALL_ITEMS", itemList);
                    this.sendToClient(response);
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
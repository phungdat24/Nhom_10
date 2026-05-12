package com.nhomX.example.networking;

import com.nhomX.example.model.*;
import com.nhomX.example.repository.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    // Gọi kho chứa dữ liệu ra để sẵn sàng làm việc
    private final ItemRepository itemRepository ;
    private final UserRepository userRepository ;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    // Lưu thông tin user dùng trong logging
    private User currentUser;

    public ClientHandler(Socket socket, AuctionServer server, ItemRepository itemRepo, UserRepository userRepo, BidRepository bidRepo, AuctionRepository auctionRepo) {
        this.socket = socket;
        this.server = server;
        this.itemRepository = itemRepo;
        this.userRepository = userRepo;
        this.bidRepository = bidRepo;
        this.auctionRepository = auctionRepo;
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
                    Object[] bidData = (Object[]) msgFromClient.getData();
                    // Đọc dữ liệu do Client gửi:
                    String userId = (String) bidData[0];
                    String auctionId = (String) bidData[1];
                    long bidAmount = msgFromClient.getAmount();
                    String newBidId = UUID.randomUUID().toString();

                    // Gọi Database Transaction
                    boolean isSuccess = bidRepository.executeBidTransaction(userId, auctionId, bidAmount, newBidId);
                    if (isSuccess) {
                        // Nếu trừ tiền và ghi DB thành công, mới báo cáo cho cả Server biết
                        Message update = new Message("UPDATE_PRICE", msgFromClient.getUsername(),
                                auctionId, bidAmount);
                        server.broadcastToAuction(auctionId, update);

                        // Báo riêng cho người đặt giá là thành công
                        this.sendToClient(new Message("BID_SUCCESS", "Bạn đã đặt giá thành công!"));
                    } else {
                        // Nếu thất bại (Lỗi DB hoặc không đủ tiền), báo lỗi về cho riêng Client này
                        this.sendToClient(new Message("BID_FAIL", "Đặt giá thất bại! Vui lòng kiểm tra lại số dư."));
                    }
                }
                // Khi Client click vào xem một sản phẩm
                else if ("WATCH_ITEM".equals(msgFromClient.getType())) {
                    server.watchAuction(msgFromClient.getAuctionId(), this);
                }
                // Khi Client quay lại màn hình chính hoặc xem sản phẩm khác
                else if ("UNWATCH_ITEM".equals(msgFromClient.getType())) {
                    server.unwatchAuction(msgFromClient.getAuctionId(), this);
                }
                else if ("LOGIN".equals(msgFromClient.getType())) {
                    // 1. Mở gói hàng lấy dữ liệu Client gửi
                    String[] data = (String[]) msgFromClient.getData();
                    String email = data[0];
                    String pass = data[1];

                    // 2. Chọc xuống Database kiểm tra
                    User loggedInUser = userRepository.login(email, pass);

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
                    long balance = (Long) data[3];

                    String newId = UUID.randomUUID().toString();
                    // Khởi tạo ngươ dùng:
                    RegularUser newUser = new RegularUser(newId, email, pass, name, balance);

                    // Cấp quyền mặc định cho họ để họ có thể Mua và Bán
                    newUser.addRole(Role.BIDDER);
                    newUser.addRole(Role.SELLER);

                    boolean isSuccess = userRepository.register(newUser);

                    if (isSuccess) {
                        this.sendToClient(new Message("REGISTER_SUCCESS", "Tạo tài khoản thành công!"));
                    } else {
                        this.sendToClient(new Message("REGISTER_FAIL", "Email đã tồn tại!"));
                    }
                }else if ("GET_ALL_AUCTIONS".equals(msgFromClient.getType())) {
                    // Server chọc vào DB lấy 10 item
                    List<Auction> auctionsList = auctionRepository.findAllActiveAuctions();

                    // Đóng gói danh sách gửi trả lại ĐÚNG cái Client vừa xin
                    Message response = new Message("RETURN_ALL_AUCTIONS", auctionsList);
                    this.sendToClient(response);
                }
            }
        } catch (Exception e) {
            System.out.println("SERVER: Client ngắt kết nối.");
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * Gửi message về client.
     * [FIX] Thêm synchronized để thread-safe – nhiều thread có thể gọi đồng thời
     * (ví dụ: Scheduler gọi khi phiên hết giờ, đồng thời Client đang nhận broadcast).
     */
    public void sendToClient(Message msg) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("SERVER: Không thể gửi message tới client – " + e.getMessage());
            cleanup();
        }
    }

    private void cleanup() {
        server.removeClient(this);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("SERVER: Lỗi đóng socket – " + e.getMessage());
        }
    }
}
package com.nhomX.example.networking;

import com.nhomX.example.model.*;
import com.nhomX.example.repository.*;
import com.nhomX.example.service.EmailService;
import com.nhomX.example.service.GmailServiceImpl;
import com.nhomX.example.utils.ValidatorUtils;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private Object[] temRegisterData;
    private String tempOtpCode;

    private volatile boolean cleaned = false;

    public ClientHandler(Socket socket, AuctionServer server,
                         ItemRepository itemRepo, UserRepository userRepo,
                         BidRepository bidRepo, AuctionRepository auctionRepo) {
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
            while ((msgFromClient = (Message) in.readObject()) != null) {
                dispatch(msgFromClient);
            }

        }  catch (EOFException | java.net.SocketException e) {
            // Client ngắt kết nối bình thường — không cần log stack trace
            System.out.println("SERVER: Client ngắt kết nối ("
                    + (currentUser != null ? currentUser.getUserName() : "chưa đăng nhập") + ")");

        } catch (IOException e) {
            System.err.println("SERVER: Lỗi I/O với client – " + e.getMessage());

        } catch (ClassNotFoundException e) {
            // BUG FIX: Tách riêng ClassNotFoundException thay vì bắt Exception rộng
            // để không vô tình che giấu NullPointerException hay ClassCastException
            System.err.println("SERVER: Nhận được class không xác định từ client – " + e.getMessage());

        } finally {
            cleanup();
        }
    }

    private void dispatch(Message msg) {
        System.out.println("SERVER NHẬN: " + msg);
        try {
            switch (msg.getType()) {
                case "BID":           handleBid(msg);          break;
                case "WATCH_ITEM":    handleWatchItem(msg);     break;
                case "UNWATCH_ITEM":  handleUnwatchItem(msg);   break;
                case "LOGIN":         handleLogin(msg);         break;
                case "GET_ALL_AUCTIONS": handleGetAllAuctions(); break;
                case "GET_BID_HISTORY":  handleGetBidHistory(msg); break;
                case "SETUP_AUTO_BID":   handleSetupAutoBid(msg);  break;  // BUG FIX: thiếu handler này
                case "GET_DASHBOARD_DATA":
                    List<Auction> endingSoonlist = auctionRepository.getEndingSoonAuctions(5);
                    List<Auction> trendingList = auctionRepository.getTrendingAuctions(10);
                    Map<String, Integer> stats = new HashMap<>();
                    stats.put("active", auctionRepository.countActiveAuctions());
                    stats.put("ending", auctionRepository.countEndingSoonAuctions());
                    stats.put("users", userRepository.getTotalUserCount());
                    stats.put("online", server.getOnlineUserCount());
                    Object[] dashboardPayload = {stats, endingSoonlist, trendingList};
                    Message responseMsg = new Message("DASHBOARD_DATA_RESULT", dashboardPayload);
                    sendToClient(responseMsg);
                    break;
                case "GET_MY_AUCTIONS":
                    String userIdForMyAuctions = (String) msg.getData();
                    System.out.println("SERVER: Đang truy vấn danh sách đấu giá cho User:" + userIdForMyAuctions);

                    try{
                        List<MyAuctionDTO> myAuctionList =  auctionRepository.getMyAuctions(userIdForMyAuctions);
                        Message responseMyAuctions = new Message("MY_AUCTIONS_RESULT", myAuctionList);
                        this.sendToClient(responseMyAuctions);
                        System.out.println("SERVER: Đã gửi " + myAuctionList.size());
                    } catch (Exception e){
                        System.err.println("Lỗi khi xử lý GET_MY_AUCTIONS: " + e.getMessage());
                        this.sendToClient(new Message("ERROR", "Không thể lấy danh sách đấu giá"));
                    }
                    break;
                case "REGISTER":
                    Object[] data = (Object[]) msg.getData();
                    String email = (String) data[0];

                    if (email == null || !ValidatorUtils.isValidEmail(email)){
                        this.sendToClient(new Message("REGISTER_FAIL","Email không đúng định dạng"));
                        break;
                    }
                    if (userRepository.findByEmail(email) != null){
                        this.sendToClient(new Message("REGISTER_FAIL","Email này đã được sử dụng"));
                        break;
                    }
                    int randomPin = (int) (Math.random() * 900000) + 100000;
                    this.tempOtpCode = String.valueOf(randomPin);
                    this.temRegisterData = data;
                    EmailService emailService = new GmailServiceImpl();
                    emailService.sendOtp(email, tempOtpCode);
                    this.sendToClient(new Message("SHOW_OTP-_DIALOG",null));
                    break;
                case "VERIFY_REGISTER_OTP":
                    String clientOtp = (String) msg.getData();
                    if (this.tempOtpCode != null && this.tempOtpCode.equals(clientOtp)){
                        String regEmail = (String) temRegisterData[0];
                        String regPass = (String) temRegisterData[1];
                        String regName = (String) temRegisterData[2];

                        boolean success = true;
                        if(success){
                            this.sendToClient(new Message("REGISTER_SUCCESS","Đăng ký tài khoản"));
                        }else{
                            this.sendToClient(new Message("REGISTER_FAIL","Lỗi hệ thống khi "));
                        }
                        this.tempOtpCode = null;
                        this.temRegisterData = null;
                    }else{
                        this.sendToClient(new Message("REGISTER_FAIL","Mã xác thực OTP không chính xác"));
                    }
                    break;
                default:
                    sendToClient(Message.error("Lệnh không xác định: " + msg.getType()));
            }
        } catch (ClassCastException e) {
            System.err.println("SERVER: Dữ liệu không đúng định dạng từ client – " + e.getMessage());
            sendToClient(Message.error("Dữ liệu gửi lên không hợp lệ!"));
        }
    }

    private void handleBid(Message msg) {
        Object[] bidData  = (Object[]) msg.getData();
        String   userId   = (String) bidData[0];
        String   auctionId = msg.getAuctionId();    // BUG FIX: dùng getAuctionId() thay vì bidData[1]
        long     bidAmount = msg.getAmount();
        String   newBidId  = UUID.randomUUID().toString();

        boolean isSuccess = bidRepository.executeBidTransaction(userId, auctionId, bidAmount, newBidId);
        if (isSuccess) {
            server.broadcastToAuction(auctionId,
                    Message.updatePrice(msg.getUsername(), auctionId, bidAmount));
            sendToClient(Message.bidSuccess());
        } else {
            sendToClient(Message.bidFail("Đặt giá thất bại! Kiểm tra lại số dư hoặc giá đặt."));
        }
    }
    private void handleWatchItem(Message msg) {
        server.watchAuction(msg.getAuctionId(), this);
    }

    private void handleUnwatchItem(Message msg) {
        server.unwatchAuction(msg.getAuctionId(), this);
    }
    private void handleLogin(Message msg) {
        String[] data = (String[]) msg.getData();
        String username = data[0];
        String password = data[1];

        User loggedInUser = userRepository.login(username, password);
        if (loggedInUser != null) {
            // BUG FIX: Set currentUser sau khi login thành công để logging có ý nghĩa
            this.currentUser = loggedInUser;
            sendToClient(Message.loginSuccess(loggedInUser));
        } else {
            sendToClient(Message.loginFail("Sai tên đăng nhập hoặc mật khẩu!"));
        }
    }
    private void handleRegister(Message msg) {
        Object[] data    = (Object[]) msg.getData();
        String   username = (String) data[0];
        String   password = (String) data[1];
        String   fullName = (String) data[2];
        long     balance  = (Long)   data[3];

        RegularUser newUser = new RegularUser(
                UUID.randomUUID().toString(), username, password, fullName, balance);
        newUser.addRole(Role.BIDDER);
        newUser.addRole(Role.SELLER);

        boolean isSuccess = userRepository.register(newUser);
        sendToClient(isSuccess
                ? Message.registerSuccess()
                : Message.registerFail("Tên đăng nhập đã tồn tại!"));
    }
    private void handleGetAllAuctions() {
        List<Auction> auctions = auctionRepository.findAllActiveAuctions();
        sendToClient(Message.returnAllAuctions(auctions));
    }

    private void handleGetBidHistory(Message msg) {
        // BUG FIX: Dùng getAuctionId() thay vì getData() để nhất quán
        // (AuctionClient.getBidHistory đã gửi auctionId vào field auctionId của Message)
        String auctionId = msg.getAuctionId();
        List<BidTransaction> history = bidRepository.getBidsByAuctionId(auctionId);
        sendToClient(Message.returnBidHistory(history));
    }
    private void handleSetupAutoBid(Message msg) {
        if (currentUser == null) {
            sendToClient(Message.autoBidFail("Bạn cần đăng nhập trước!"));
            return;
        }
        Object[] data      = (Object[]) msg.getData();
        String   auctionId = (String) data[0];
        long     maxLimit  = (Long)   data[1];
        long     increment = (Long)   data[2];
        String   userId    = currentUser.getId();

        boolean isSuccess = bidRepository.saveAutoBidConfig(userId, auctionId, maxLimit, increment);
        sendToClient(isSuccess
                ? Message.autoBidSuccess()
                : Message.autoBidFail("Không thể thiết lập Auto-Bid. Vui lòng thử lại!"));
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
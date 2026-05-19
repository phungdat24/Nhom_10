package com.nhomX.example.networking;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.exception.AuthenticationException;
import com.nhomX.example.exception.InvalidBidException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.service.EmailService;
import com.nhomX.example.service.GmailServiceImpl;
import com.nhomX.example.utils.ValidatorUtils;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    // Gọi kho chứa dữ liệu ra để sẵn sàng làm việc
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    // Lưu thông tin user dùng trong logging
    private User currentUser;
    private Object[] tempRegisterData;
    private String tempOtpCode;
    private LocalDateTime otpCreationTime;

    private volatile boolean cleaned = false;

    public ClientHandler(Socket socket, AuctionServer server, ItemRepository itemRepo,
            UserRepository userRepo, BidRepository bidRepo, AuctionRepository auctionRepo) {
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

        } catch (EOFException | java.net.SocketException e) {
            // Client ngắt kết nối bình thường — không cần log stack trace
            System.out.println("SERVER: Client ngắt kết nối ("
                    + (currentUser != null ? currentUser.getUserName() : "chưa đăng nhập") + ")");

        } catch (IOException e) {
            System.err.println("SERVER: Lỗi I/O với client – " + e.getMessage());

        } catch (ClassNotFoundException e) {
            // BUG FIX: Tách riêng ClassNotFoundException thay vì bắt Exception rộng
            // để không vô tình che giấu NullPointerException hay ClassCastException
            System.err.println(
                    "SERVER: Nhận được class không xác định từ client – " + e.getMessage());

        } finally {
            cleanup();
        }
    }

    private void dispatch(Message msg) {
        System.out.println("SERVER NHẬN: " + msg);
        try {
            switch (msg.getType()) {
                case "BID":
                    handleBid(msg);
                    break;
                case "WATCH_ITEM":
                    handleWatchItem(msg);
                    break;
                case "UNWATCH_ITEM":
                    handleUnwatchItem(msg);
                    break;
                case "LOGIN":
                    handleLogin(msg);
                    break;
                case "GET_ALL_AUCTIONS":
                    handleGetAllAuctions();
                    break;
                case "GET_BID_HISTORY":
                    handleGetBidHistory(msg);
                    break;
                case "SETUP_AUTO_BID":
                    handleSetupAutoBid(msg);
                    break; // BUG FIX: thiếu handler này
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
                    System.out.println("SERVER: Đang truy vấn danh sách đấu giá cho User:"
                            + userIdForMyAuctions);

                    try {
                        List<MyAuctionDTO> myAuctionList =
                                auctionRepository.getMyAuctions(userIdForMyAuctions);
                        Message responseMyAuctions =
                                new Message("MY_AUCTIONS_RESULT", myAuctionList);
                        this.sendToClient(responseMyAuctions);
                        System.out.println("SERVER: Đã gửi " + myAuctionList.size());
                    } catch (Exception e) {
                        System.err.println("Lỗi khi xử lý GET_MY_AUCTIONS: " + e.getMessage());
                        this.sendToClient(new Message("ERROR", "Không thể lấy danh sách đấu giá"));
                    }
                    break;
                case "REGISTER":
                    Object[] data = (Object[]) msg.getData();
                    String email = (String) data[0];

                    if (email == null || !ValidatorUtils.isValidEmail(email)) {
                        this.sendToClient(
                                new Message("REGISTER_FAIL", "Email không đúng định dạng"));
                        break;
                    }
                    if (userRepository.findByUsername(email) != null) {
                        this.sendToClient(
                                new Message("REGISTER_FAIL", "Email này đã được sử dụng"));
                        break;
                    }
                    int randomPin = (int) (Math.random() * 900000) + 100000;
                    this.tempOtpCode = String.valueOf(randomPin);
                    this.tempRegisterData = data;
                    this.otpCreationTime = LocalDateTime.now();
                    EmailService emailService = new GmailServiceImpl();
                    // 3. Gọi hàm gửi mail và ĐỢI KẾT QUẢ (thenAccept)
                    emailService.sendOtp(email, this.tempOtpCode).thenAccept(isSuccess -> {
                        if (isSuccess) {
                            // Gửi mail CÓ THẬT và THÀNH CÔNG -> Ra lệnh cho Client mở màn hình 6 ô
                            // OTP
                            this.sendToClient(new Message("SHOW_OTP_DIALOG", "Đã gửi mã OTP."));
                        } else {
                            // Gửi mail THẤT BẠI (Email ảo, sai định dạng, Google chặn...)
                            // Xóa cache rác
                            this.tempOtpCode = null;
                            this.tempRegisterData = null;
                            this.otpCreationTime = null; // Dọn dẹp nếu lỗi
                            // Báo lỗi, Client sẽ vẫn đứng ở màn hình đăng ký ban đầu
                            this.sendToClient(new Message("REGISTER_FAIL",
                                    "Không thể gửi email. Hãy kiểm tra lại địa chỉ Email!"));
                        }
                    });
                    break;
                case "VERIFY_REGISTER_OTP":
                    String clientOtp = (String) msg.getData();
                    // 1. Kiểm tra xem mã đã quá hạn 5 phút chưa
                    if (this.otpCreationTime == null || Duration
                            .between(this.otpCreationTime, LocalDateTime.now()).toMinutes() > 5) {

                        this.sendToClient(new Message("REGISTER_FAIL",
                                "Mã xác thực OTP đã hết hạn (Quá 5 phút). Vui lòng gửi lại mã mới!"));
                        // Xóa toàn bộ dữ liệu tạm cũ để bảo mật
                        this.tempOtpCode = null;
                        this.tempRegisterData = null;
                        this.otpCreationTime = null;
                        break;
                    }
                    if (this.tempOtpCode != null && this.tempOtpCode.equals(clientOtp)) {
                        String regEmail = (String) tempRegisterData[0];
                        String regPass = (String) tempRegisterData[1];
                        String regName = (String) tempRegisterData[2];
                        RegularUser newUser = new RegularUser(UUID.randomUUID().toString(),
                                regEmail, regPass, regName, 0L);
                        newUser.addRole(Role.BIDDER);
                        newUser.addRole(Role.SELLER);

                        boolean success = userRepository.register(newUser);
                        if (success) {
                            this.sendToClient(new Message("REGISTER_SUCCESS",
                                    "Đăng ký tài khoản thành công"));
                        } else {
                            this.sendToClient(new Message("REGISTER_FAIL", "Lỗi hệ thống khi "));
                        }
                        // Dọn dẹp bộ nhớ đệm
                        this.tempOtpCode = null;
                        this.tempRegisterData = null;
                        this.otpCreationTime = null;
                    } else {
                        this.sendToClient(
                                new Message("REGISTER_FAIL", "Mã xác thực OTP không chính xác"));
                    }
                    break;
                case "RESEND_OTP":
                    // 1. Kiểm tra xem người dùng này có đang trong phiên chờ OTP không
                    if (this.tempRegisterData != null) {
                        String regEmail = (String) this.tempRegisterData[0]; // Lấy lại email cũ ra

                        // 2. Tạo mã OTP hoàn toàn mới
                        int newRandomPin = (int) (Math.random() * 900000) + 100000;
                        this.tempOtpCode = String.valueOf(newRandomPin);

                        // 3. Reset lại mốc thời gian 5 phút cho mã mới này
                        this.otpCreationTime = LocalDateTime.now();

                        System.out.println("SERVER: Đang tiến hành gửi LẠI mã OTP "
                                + this.tempOtpCode + " tới " + regEmail);

                        // 4. Giao cho GmailService gửi đi
                        EmailService resendService = new GmailServiceImpl();
                        resendService.sendOtp(regEmail, this.tempOtpCode).thenAccept(isSuccess -> {
                            if (isSuccess) {
                                // Gửi thành công, báo cho Client biết (Mặc dù Client không cần
                                // chuyển cảnh nữa)
                                System.out.println("SERVER: Đã gửi lại thư thành công!");
                            } else {
                                this.sendToClient(new Message("REGISTER_FAIL",
                                        "Lỗi đường truyền, không thể gửi lại email!"));
                            }
                        });
                    } else {
                        // Nếu user treo máy quá lâu bị xóa cache, bắt họ quay lại đăng ký từ đầu
                        this.sendToClient(new Message("REGISTER_FAIL",
                                "Phiên đăng ký đã hết hạn. Vui lòng quay lại màn hình ban đầu!"));
                    }
                    break;
                case "APPROVE_AUCTION":
                    String[] approvalData = (String[]) msg.getData();
                    String auctionToApprove = approvalData[0];
                    String adminId = approvalData[1];

                    Auction a = auctionRepository.findById(auctionToApprove);
                    if (a != null && a.getStatus() == AuctionStatus.PENDING) {
                        a.setApprovedBy(adminId);
                        auctionRepository.updateAuctionStatus(a);
                        sendToClient(new Message("APPOVE_SUCCESS", "Đã duyệt thành công"));
                    }
                    break;
                default:
                    sendToClient(Message.error("Lệnh không xác định: " + msg.getType()));
            }
        } catch (ClassCastException e) {
            System.err
                    .println("SERVER: Dữ liệu không đúng định dạng từ client – " + e.getMessage());
            sendToClient(Message.error("Dữ liệu gửi lên không hợp lệ!"));
        }
    }

    private void handleBid(Message msg) {
        Object[] bidData = (Object[]) msg.getData();
        String userId = (String) bidData[0];
        String auctionId = msg.getAuctionId(); // BUG FIX: dùng getAuctionId() thay vì bidData[1]
        long bidAmount = msg.getAmount();
        String newBidId = UUID.randomUUID().toString();
        try {
            // Ném Exception ngay tại cổng nếu chưa đăng nhập
            if (this.currentUser == null) {
                throw new AuthenticationException(
                        "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn trên máy chủ!");
            }
            boolean isSuccess =
                    bidRepository.executeBidTransaction(userId, auctionId, bidAmount, newBidId);
            if (isSuccess) {
                String bidderFullName =
                        (this.currentUser.getFullName() != null) ? this.currentUser.getFullName()
                                : msg.getUsername();
                server.broadcastToAuction(auctionId,
                        Message.updatePrice(bidderFullName, auctionId, bidAmount));
                sendToClient(Message.bidSuccess());
            }
        } catch (InvalidBidException | AuthenticationException | AuctionClosedException e) {
            // BẮT LỖI NGHIỆP VỤ: Gửi chính xác thông báo lỗi về cho người dùng
            sendToClient(Message.bidFail(e.getMessage()));
        } catch (Exception e) {
            // BẮT LỖI HỆ THỐNG (Lỗi Database, NullPointer, v.v.): Tránh làm sập Server
            System.err.println("SERVER: Lỗi hệ thống khi xử lý BID - " + e.getMessage());
            sendToClient(Message.error("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau!"));
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
        Object[] data = (Object[]) msg.getData();
        String auctionId = (String) data[0];
        long maxLimit = (Long) data[1];
        long increment = (Long) data[2];
        String userId = currentUser.getId();

        boolean isSuccess = bidRepository.saveAutoBidConfig(userId, auctionId, maxLimit, increment);
        sendToClient(isSuccess ? Message.autoBidSuccess()
                : Message.autoBidFail("Không thể thiết lập Auto-Bid. Vui lòng thử lại!"));
    }

    /**
     * Gửi message về client. [FIX] Thêm synchronized để thread-safe – nhiều thread có thể gọi đồng
     * thời (ví dụ: Scheduler gọi khi phiên hết giờ, đồng thời Client đang nhận broadcast).
     */
    public void sendToClient(Message msg) {
        if (out == null)
            return;
        try {
            synchronized (out) {
                out.writeObject(msg);
                out.flush();
                out.reset(); // Ngăn chặn Java cache lại dữ liệu cũ (Lost Update bề mặt)
            }
        } catch (IOException e) {
            System.err.println("SERVER: Không thể gửi message tới client – " + e.getMessage());
            // Nếu gửi xịt (Client rút dây mạng), lập tức dọn dẹp
            cleanup();
        }
    }

    private void cleanup() {
        server.removeClient(this);
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            System.err.println("SERVER: Lỗi đóng socket – " + e.getMessage());
        }
    }
}

package com.nhomX.example.networking;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.exception.AuthenticationException;
import com.nhomX.example.exception.InvalidBidException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.service.AuctionService;
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
    // THAY ĐỔI 1: Lưu ảnh ra ngoài JAR, vào thư mục tuyệt đối bên cạnh file chạy
// =========================================================================
    private static final String IMAGE_DIR =
            System.getProperty("user.dir") + File.separator + "auction_images" + File.separator;

    public static final ConcurrentHashMap<String,OtpData> otpStorage = new ConcurrentHashMap<>();

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
                case "CREATE_AUCTION_REQUEST":
                    handleCreateAuctionRequest(msg);
                    break;
                case "GET_DASHBOARD_DATA":
                    handleGetDashboardData();
                    break;
                case "GET_MY_AUCTIONS":
                    handleGetMyAuctions(msg);
                    break;
                case "REGISTER":
                    handleRegister(msg);
                    break;
                case "VERIFY_REGISTER_OTP":
                    handleVerifyRegisterOtp(msg);
                    break;
                case "RESEND_OTP":
                    handleResendOtp(msg);
                    break;
                case "APPROVE_AUCTION":
                    handleApproveAuction(msg);
                    break;
                case "FORGOT_PASSWORD_REQUEST":
                    handleForgotPasswordRequest(msg);
                    break;
                case "RESET_PASSWORD":
                    handleResetPassword(msg);
                    break;
                case "VERIFY_FORGOT_PW_OTP":
                    handleVerifyForgotPwOtp(msg);
                    break;
                case "GET_SELLER_AUCTIONS":
                    String sellerId = (String) msg.getData();
                    System.out.println("SERVER: Đang truy vấn danh sách bán hàng cho user");
                    try{
                        List<Auction> sellerList = auctionRepository.findBySellerId(sellerId);
                        sendToClient(new Message("SELLER_AUCTIONS_RESULT", sellerList));
                    } catch (Exception e){
                        sendToClient(new Message("ERROR", "Không thể lấy danh sách bán hàng"));
                    }
                    break;
                case "DEPOSIT_REQUEST":
                    handleDepositRequest(msg);
                    break;
                case "GET_IMAGE":
                    handleGetImage(msg);
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

    private void handleDepositRequest(Message msg) {
        try{
            // Giải nén payload: [userId, amount, content, bankName]
            Object[] payload = (Object[]) msg.getData();
            String userId = (String) payload[0];
            long amount = (Long) payload[1];

            // [REFACTOR] Guard: Kiểm tra amount hợp lệ
            if (amount <= 0) {
                System.err.println("SERVER SECURITY: Phát hiện amount không hợp lệ: " + amount);
                sendToClient(new Message("DEPOSIT_RESULT", new Object[]{ false, 0L }));
                return;
            }

            // [REFACTOR] Guard: Đảm bảo userId khớp với session hiện tại — chống giả mạo
            if (currentUser == null || !currentUser.getId().equals(userId)) {
                System.err.println("SERVER SECURITY: userId không khớp session, từ chối nạp tiền.");
                sendToClient(new Message("DEPOSIT_RESULT", new Object[]{ false, 0L }));
                return;
            }

            userRepository.updateBalance(userId, amount);
            User updatedUser = userRepository.findById(userId);


            Message response;
            if (updatedUser != null) {
                System.out.println("SERVER: Nạp thành công" + amount + "cho" + userId);
                Object[] responseData = {true, updatedUser.getBalance()};
                response = new Message("DEPOSIT_RESULT", responseData);
            } else {
                Object[] responseData = {false, 0L};
                response = new Message("DEPOSIT_RESULT", responseData);
            }
            sendToClient(response);
        } catch (Exception e) {
            System.out.println("SERVER LỖI: Xử lý giao dịch nạp tiền thất bại - " + e.getMessage());
            Object[] responseData = {false, 0L};
            sendToClient(new Message("ERROR", responseData));
        }
    }

    private void handleCreateAuctionRequest(Message msg) {
        try {
            Object[] payload = (Object[]) msg.getData();
            Items item = (Items) payload[0];
            Auction auction = (Auction) payload[1];
            @SuppressWarnings("unchecked")
            Map<String, byte[]> imageDataMap = (Map<String, byte[]>) payload[2];

            // Tạo thư mục lưu ảnh nếu chưa có (ngoài JAR, bền vững khi restart)
            File dir = new File(IMAGE_DIR);
            if (!dir.exists()) dir.mkdirs();

            if (imageDataMap != null) {
                // Lưu ảnh xuống ổ cứng và update đường dẫn vao item
                for (Map.Entry<String, byte[]> entry : imageDataMap.entrySet()) {
                    String fileName = entry.getKey();
                    byte[] imageBytes = entry.getValue();
                    // Tạo file lưu dữ liệu đầu vào
                    File imageFile = new File(IMAGE_DIR + fileName);
                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        fos.write(imageBytes);
                    }
                    // Chỉ lưu tên file — không lưu đường dẫn tuyệt đối
                    item.addImage(new ItemImage(
                            UUID.randomUUID().toString(),
                            fileName,          // ← CHỈ LƯU TÊN FILE: "item_abc123_0.jpg"
                            item.getId()
                    ));
                }
            }
            // Gọi server lưu DB
            AuctionService auctionService = new AuctionService();
            boolean isSuccess = auctionService.createAuctionListing(item, auction);
            // Trả về kết quả Client
            sendToClient(new Message("CREATE_AUCTION_RESULT",
                    new String[]{ String.valueOf(isSuccess),
                            isSuccess ? "Tạo phiên đấu giá thành công." : "Lưu phiên đấu giá thất bại." }
            ));

        } catch (Exception e) {
            System.err.println("SERVER LỖI: Xử lý ảnh thất bại - " + e.getMessage());
            sendToClient(new Message("CREATE_AUCTION_RESULT",
                    new String[]{ "false", "Lỗi server khi tạo phiên đấu giá." }));
        }
    }
    /**
     * Validate tên file chống Path Traversal.
     * Chỉ cho phép: chữ cái, số, gạch dưới, gạch ngang, dấu chấm.
     * Từ chối: "../", "/", "\", ký tự đặc biệt.
     */
    private boolean isValidImageFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        // Regex: chỉ cho phép a-z, A-Z, 0-9, _, -, và đúng 1 dấu chấm trước extension
        return fileName.matches("^[a-zA-Z0-9_\\-]+\\.(jpg|jpeg|png)$");
    }
    // THÊM MỚI: Handler trả ảnh về Client dưới dạng byte[]
// =========================================================================
    private void handleGetImage(Message msg) {
        String fileName = (String) msg.getData();
        // [REFACTOR] Guard: Validate tên file — chống Path Traversal
        if (!isValidImageFileName(fileName)) {
            System.err.println("SERVER SECURITY: Yêu cầu ảnh với tên file không hợp lệ: " + fileName);
            sendToClient(new Message("IMAGE_RESULT", null));
            return;
        }
        File   imageFile = new File(IMAGE_DIR + fileName);

        if (!imageFile.exists()) {
            sendToClient(new Message("IMAGE_RESULT", null));
            System.err.println("SERVER: Không tìm thấy ảnh: " + fileName);
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            // Payload: [fileName, byte[]] để Client biết ảnh nào vừa về
            sendToClient(new Message("IMAGE_RESULT", new Object[]{ fileName, imageBytes }));
        } catch (IOException e) {
            System.err.println("SERVER: Lỗi đọc ảnh " + fileName + " - " + e.getMessage());
            sendToClient(new Message("IMAGE_RESULT", null));
        }
    }
    private void handleGetDashboardData() {
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
    }

    private void handleGetMyAuctions(Message msg) {
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
    }

    private void handleRegister(Message msg) {
        Object[] data = (Object[]) msg.getData();
        String email = (String) data[0];

        if (email == null || !ValidatorUtils.isValidEmail(email)) {
            this.sendToClient(
                    new Message("REGISTER_FAIL", "Email không đúng định dạng"));
            return;
        }
        if (userRepository.findByUsername(email) != null) {
            this.sendToClient(
                    new Message("REGISTER_FAIL", "Email này đã được sử dụng"));
            return;
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
    }

    private void handleVerifyRegisterOtp(Message msg) {
        String[] otpPayload = (String[]) msg.getData();
        // otpPayload[0] là email, ta có thể bỏ qua nếu đăng ký đang dùng tempRegisterData
        String clientOtp = otpPayload[1]; // Lấy đúng mã OTP ở vị trí số 1
        // 1. Kiểm tra xem mã đã quá hạn 5 phút chưa
        if (this.otpCreationTime == null || Duration
                .between(this.otpCreationTime, LocalDateTime.now()).toMinutes() > 5) {

            this.sendToClient(new Message("REGISTER_FAIL",
                    "Mã xác thực OTP đã hết hạn (Quá 5 phút). Vui lòng gửi lại mã mới!"));
            // Xóa toàn bộ dữ liệu tạm cũ để bảo mật
            this.tempOtpCode = null;
            this.tempRegisterData = null;
            this.otpCreationTime = null;
            return;
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
    }

    private void handleResendOtp(Message msg) {
        String[] resendPayload = (String[]) msg.getData();
        String targetEmail = resendPayload[0];
        String flow = resendPayload[1]; // "FORGOT_PASSWORD" hoặc "REGISTER"
        // 1. Tạo mã OTP mới (Dùng chung cho cả 2 luồng)
        String newOtpCode = String.format("%06d", new java.util.Random().nextInt(999999));

        // [TỐI ƯU]: Kiểm tra xem có phiên làm việc hợp lệ không
        boolean isSessionValid = false;
        if ("FORGOT_PASSWORD".equals(flow)) {
            otpStorage.put(targetEmail, new OtpData(newOtpCode));
        } else {
            // Luồng Đăng ký: cập nhật biến cục bộ
            this.tempOtpCode = newOtpCode;
            this.otpCreationTime = LocalDateTime.now();
        }
        System.out.println("SERVER: Đang gửi lại mã OTP [" + newOtpCode + "] tới " + targetEmail);
        EmailService resendService = new GmailServiceImpl();
        resendService.sendOtp(targetEmail, newOtpCode).thenAccept(isSuccess -> {
            if (isSuccess) {
                // Gửi thành công, báo cho Client biết (Mặc dù Client không cần
                // chuyển cảnh nữa)
                System.out.println("SERVER: Đã gửi lại thư thành công!");
            } else {
                this.sendToClient(new Message("REGISTER_FAIL",
                            "Lỗi đường truyền, không thể gửi lại email!"));
            }
        });
    }

    private void handleApproveAuction(Message msg) {
        String[] approvalData = (String[]) msg.getData();
        String auctionToApprove = approvalData[0];
        String adminId = approvalData[1];

        Auction a = auctionRepository.findById(auctionToApprove);
        if (a != null && a.getStatus() == AuctionStatus.PENDING) {
            a.setApprovedBy(adminId);
            auctionRepository.updateAuctionStatus(a);
            sendToClient(new Message("APPOVE_SUCCESS", "Đã duyệt thành công"));
        }
    }

    public static class OtpData {
        public String code;
        public LocalDateTime expireTime;

        public OtpData(String code) {
            this.code = code;
            // Cộng thẳng 5 phút từ lúc tạo. Hết 5 phút là hết hiệu lực
            this.expireTime = LocalDateTime.now().plusMinutes(5);
        }
    }
    private void handleVerifyForgotPwOtp(Message msg) {
        try {
            // Lấy data Client gửi lên (Mảng String gồm [email, otpCode])
            String[] data = (String[]) msg.getData();
            String email = data[0];
            String clientOtp = data[1];
            // Lấy OTP từ mảng ra
            OtpData storedOtpData = otpStorage.get(email);

            Message response;
            // Kiểm tra: Có OTP trong sổ KHÔNG? VÀ nó có khớp với Client gửi lên KHÔNG?
            if (storedOtpData != null) {

                // 1. KIỂM TRA HẠN SỬ DỤNG
                if (LocalDateTime.now().isAfter(storedOtpData.expireTime)) {
                    otpStorage.remove(email); // Hết hạn thì xé nháp ngay
                    System.out.println("SERVER: Mã OTP ĐÃ HẾT HẠN cho email " + email);
                    response = new Message("FORGOT_PASSWORD_RESULT", new String[]{"false", "Mã OTP đã hết hạn (quá 5 phút). Vui lòng gửi lại mã mới!"});
                }
                // 2. KIỂM TRA TÍNH CHÍNH XÁC
                else if (storedOtpData.code.equals(clientOtp)) {
                    otpStorage.remove(email); // Dùng xong cũng xé nháp (Bảo mật 1 lần)
                    System.out.println("SERVER: Mã OTP HỢP LỆ cho email " + email);
                    response = new Message("FORGOT_PASSWORD_RESULT", new String[]{"true", "Xác thực OTP thành công!"});
                }// 3. NHẬP SAI
                else {
                    System.out.println("SERVER: Mã OTP SAI cho email " + email);
                    response = new Message("FORGOT_PASSWORD_RESULT", new String[]{"false", "Mã xác thực OTP không chính xác!"});

                }
            } else {
                System.out.println("SERVER: Mã OTP SAI HOẶC ĐÃ HẾT HẠN cho email " + email);
                String[] responseData = {"false", "Mã xác thực OTP không chính xác hoặc đã hết hạn!"};
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            }

            // Gửi kết quả về lại Client
            sendToClient(response);

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi xác thực OTP: " + e.getMessage());
            sendToClient(Message.error("Lỗi hệ thống khi xác thực OTP."));
        }
    }
    private void handleResetPassword(Message msg) {
        try {
            String[] data = (String[]) msg.getData();
            String email = data[0];
            String newPasswordHash = data[1];

            // Gọi Repository để ghi đè mật khẩu mới
            boolean isUpdated = userRepository.updatePassword(email, newPasswordHash);

            Message response;
            if (isUpdated) {
                System.out.println("SERVER: Đã cập nhật mật khẩu mới cho user " + email);
                String[] responseData = {"true", "Đổi mật khẩu thành công"};
                // Mượn lại tín hiệu FORGOT_PASSWORD_RESULT để trả về cho Client
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            } else {
                String[] responseData = {"false", "Lỗi CSDL: Không thể cập nhật mật khẩu"};
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            }
            sendToClient(response);
        } catch (Exception e) {
            System.err.println("SERVER LỖI: " + e.getMessage());
        }
    }

    private void handleForgotPasswordRequest(Message msg) {
        try{
            String[] data = (String[]) msg.getData();
            String email = data[0];
            boolean isEmailExist = userRepository.findByUsername(email) != null;

            if (isEmailExist) {
                String otpCode = String.format("%06d", new java.util.Random().nextInt(999999));
                otpStorage.put(email, new OtpData(otpCode));
                System.out.println("SERVER: Đã tạo OTP [" + otpCode + "] cho quên mật khẩu.");
                EmailService emailService = new GmailServiceImpl();
                emailService.sendOtp(email, otpCode).thenAccept(isSuccess -> {
                    if (isSuccess) {
                        String[] responseData = {"true", "Mã OTP đã được gửi đến email của bạn"};
                        sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
                    } else {
                        // Lỗi mạng hoặc email rác -> Xóa OTP vừa tạo đi và báo lỗi
                        otpStorage.remove(email);
                        String[] responseData = {"false", "Hệ thống không thể gửi email lúc này. Vui lòng thử lại!"};
                        sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
                    }
                });
            } else {
                // Email chưa đăng ký tài khoản bao giờ
                String[] responseData = {"false", "Email này không tồn tại trong hệ thống"};
                sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi xử lý Forgot Password: " + e.getMessage());
            String[] responseData = {"false", "Lỗi máy chủ cục bộ!"};
            sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
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
                server.broadcastToAll(Message.updatePrice(bidderFullName, auctionId, bidAmount));
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

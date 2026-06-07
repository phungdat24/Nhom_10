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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhomX.example.dto.DashboardDataDTO;
import com.nhomX.example.exception.AuctionClosedException;
import com.nhomX.example.exception.AuthenticationException;
import com.nhomX.example.exception.InvalidBidException;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.AutoBidConfig;
import com.nhomX.example.model.BidTransaction;
import com.nhomX.example.model.ItemImage;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.MyAuctionDTO;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import com.nhomX.example.model.User;
import com.nhomX.example.repository.AuctionRepository;
import com.nhomX.example.repository.AutoBidRepository;
import com.nhomX.example.repository.AutoBidRepositoryImpl;
import com.nhomX.example.repository.BidRepository;
import com.nhomX.example.repository.DashboardRepository;
import com.nhomX.example.repository.ItemRepository;
import com.nhomX.example.repository.UserRepository;
import com.nhomX.example.service.AuctionService;
import com.nhomX.example.service.EmailService;
import com.nhomX.example.service.GmailServiceImpl;
import com.nhomX.example.utils.ValidatorUtils;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    // Gọi kho chứa dữ liệu ra để sẵn sàng làm việc
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final AutoBidRepository autoBidRepository;
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

    public static final ConcurrentHashMap<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket, AuctionServer server, ItemRepository itemRepo,
            UserRepository userRepo, BidRepository bidRepo, AuctionRepository auctionRepo,
            AutoBidRepository autoBidRepo) {
        this.socket = socket;
        this.server = server;
        this.itemRepository = itemRepo;
        this.userRepository = userRepo;
        this.bidRepository = bidRepo;
        this.auctionRepository = auctionRepo;
        this.autoBidRepository = autoBidRepo;
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
            logger.info("SERVER: Client ngắt kết nối ({})",
                    currentUser != null ? currentUser.getUserName() : "chưa đăng nhập");

        } catch (IOException e) {
            logger.error("SERVER: Lỗi I/O với client: {}", e.getMessage(), e);

        } catch (ClassNotFoundException e) {
            // BUG FIX: Tách riêng ClassNotFoundException thay vì bắt Exception rộng
            // để không vô tình che giấu NullPointerException hay ClassCastException
            logger.error("SERVER: Nhận được class không xác định từ client: {}", e.getMessage(), e);

        } finally {
            cleanup();
        }
    }

    private void dispatch(Message msg) {
        logger.debug("SERVER NHẬN: {}", msg);
        try {
            switch (msg.getType()) {
                // Đăng ký, xác thực qua OTP, gửi lại mã.
                case "REGISTER":
                    handleRegister(msg);
                    break;
                case "VERIFY_REGISTER_OTP":
                    handleVerifyRegisterOtp(msg);
                    break;
                case "RESEND_OTP":
                    handleResendOtp(msg);
                    break;
                // Đăng nhập và lấy lại mật khẩu.
                case "LOGIN":
                    handleLogin(msg);
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
                // Lấy danh sách phiên đấu giá (tất cả, đang chạy, chờ duyệt).
                case "GET_ALL_AUCTIONS":
                    handleGetAllAuctions();
                    break;
                case "GET_LIVE_AUCTIONS":
                    handleGetLiveAuctions();
                    break;
                case "GET_PENDING_AUCTIONS":
                    handleGetPendingAuctions();
                    break;
                // Theo dõi/Hủy theo dõi sản phẩm.
                case "WATCH_ITEM":
                    handleWatchItem(msg);
                    break;
                case "UNWATCH_ITEM":
                    handleUnwatchItem(msg);
                    break;
                // Đặt giá, xem lịch sử, cài đặt Auto-bid.
                case "BID":
                    handleBid(msg);
                    break;
                case "GET_BID_HISTORY":
                    handleGetBidHistory(msg);
                    break;
                case "SETUP_AUTO_BID":
                    handleSetupAutoBid(msg);
                    break;
                // Người bán tạo phiên và xem hàng của mình.
                case "CREATE_AUCTION_REQUEST":
                    handleCreateAuctionRequest(msg);
                    break;
                case "GET_SELLER_AUCTIONS":
                    handleGetSellerAuctions(msg);
                    break;
                // Duyệt/hủy phiên đấu giá.
                case "APPROVE_AUCTION":
                    handleApproveAuction(msg);
                    break;
                case "REJECT_AUCTION":
                    handleRejectAuction(msg);
                    break;
                case "FORCE_CANCEL_AUCTION":
                    handleForceCancelAuction(msg);
                    break;
                // Quản lý người dùng.
                case "GET_ALL_USERS":
                    handleGetAllUsers();
                    break;
                case "TOGGLE_USER_STATUS":
                    handleToggleUserStatus(msg);
                    break;
                case "DELETE_USER":
                    handleDeleteUser(msg);
                    break;
                // Biểu đồ, xem ví, nạp tiền, tải ảnh.
                case "GET_DASHBOARD_DATA":
                    handleGetDashboardData();
                    break;
                case "GET_MY_AUCTIONS":
                    handleGetMyAuctions(msg);
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
            logger.warn("SERVER: Dữ liệu không đúng định dạng từ client: {}", e.getMessage());
            sendToClient(Message.error("Dữ liệu gửi lên không hợp lệ!"));
        }
    }
    // =================================================================================================================
    // ----------------NHÓM ĐĂNG KÝ VÀ XÁC THỰC OTP---------------------------------------------------------------------
    // =================================================================================================================
    private void handleRegister(Message msg) {
        Object[] data = (Object[]) msg.getData();
        String email = (String) data[0];

        if (email == null || !ValidatorUtils.isValidEmail(email)) {
            this.sendToClient(new Message("REGISTER_FAIL", "Email không đúng định dạng"));
            return;
        }
        if (userRepository.findByUsername(email) != null) {
            this.sendToClient(new Message("REGISTER_FAIL", "Email này đã được sử dụng"));
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
        if (this.otpCreationTime == null
                || Duration.between(this.otpCreationTime, LocalDateTime.now()).toMinutes() > 5) {

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
            RegularUser newUser =
                    new RegularUser(UUID.randomUUID().toString(), regEmail, regPass, regName, 0L, true);
            newUser.addRole(Role.BIDDER);
            newUser.addRole(Role.SELLER);

            boolean success = userRepository.register(newUser);
            if (success) {
                this.sendToClient(new Message("REGISTER_SUCCESS", "Đăng ký tài khoản thành công"));
            } else {
                this.sendToClient(new Message("REGISTER_FAIL", "Lỗi hệ thống khi "));
            }
            // Dọn dẹp bộ nhớ đệm
            this.tempOtpCode = null;
            this.tempRegisterData = null;
            this.otpCreationTime = null;
        } else {
            this.sendToClient(new Message("REGISTER_FAIL", "Mã xác thực OTP không chính xác"));
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
        logger.info("SERVER: Đang gửi lại mã OTP tới email {}", targetEmail);
        EmailService resendService = new GmailServiceImpl();
        resendService.sendOtp(targetEmail, newOtpCode).thenAccept(isSuccess -> {
            if (isSuccess) {
                // Gửi thành công, báo cho Client biết (Mặc dù Client không cần
                // chuyển cảnh nữa)
                logger.info("SERVER: Đã gửi lại thư thành công.");
            } else {
                this.sendToClient(
                        new Message("REGISTER_FAIL", "Lỗi đường truyền, không thể gửi lại email!"));
            }
        });
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
    // =================================================================================================================
    // -----------------------------------NHÓM ĐĂNG NHẬP VÀ QUÊN MẬT KHẨU-----------------------------------------------
    // =================================================================================================================
    private void handleLogin(Message msg) {
        String[] data = (String[]) msg.getData();
        String username = data[0];
        String password = data[1];

        User loggedInUser = userRepository.login(username, password);

        if (loggedInUser != null) {
            // Kiểm tra xem Model có đang bị khóa không
            // ==========================================
            if (!loggedInUser.isActive()) {
                System.out.println("SERVER: Khóa đăng nhập với tài khoản bị ban - " + username);
                sendToClient(Message.loginFail("Tài khoản của bạn đã bị khóa! Vui lòng liên hệ Admin."));
                return; // Dừng tiến trình đăng nhập ngay lập tức
            }

            //Set currentUser sau khi login thành công để logging có ý nghĩa
            this.currentUser = loggedInUser;
            sendToClient(Message.loginSuccess(loggedInUser));

        } else {
            sendToClient(Message.loginFail("Sai tên đăng nhập hoặc mật khẩu!"));
        }
    }
    private void handleForgotPasswordRequest(Message msg) {
        try {
            String[] data = (String[]) msg.getData();
            String email = data[0];
            boolean isEmailExist = userRepository.findByUsername(email) != null;

            if (isEmailExist) {
                String otpCode = String.format("%06d", new java.util.Random().nextInt(999999));
                otpStorage.put(email, new OtpData(otpCode));
                logger.info("SERVER: Đã tạo OTP cho yêu cầu quên mật khẩu của email {}", email);
                EmailService emailService = new GmailServiceImpl();
                emailService.sendOtp(email, otpCode).thenAccept(isSuccess -> {
                    if (isSuccess) {
                        String[] responseData = {"true", "Mã OTP đã được gửi đến email của bạn"};
                        sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
                    } else {
                        // Lỗi mạng hoặc email rác -> Xóa OTP vừa tạo đi và báo lỗi
                        otpStorage.remove(email);
                        String[] responseData = {"false",
                                "Hệ thống không thể gửi email lúc này. Vui lòng thử lại!"};
                        sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
                    }
                });
            } else {
                // Email chưa đăng ký tài khoản bao giờ
                String[] responseData = {"false", "Email này không tồn tại trong hệ thống"};
                sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
            }

        } catch (Exception e) {
            logger.error("Lỗi xử lý Forgot Password", e);
            String[] responseData = {"false", "Lỗi máy chủ cục bộ!"};
            sendToClient(new Message("FORGOT_PASSWORD_RESULT", responseData));
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
                logger.info("SERVER: Đã cập nhật mật khẩu mới cho user {}", email);
                String[] responseData = {"true", "Đổi mật khẩu thành công"};
                // Mượn lại tín hiệu FORGOT_PASSWORD_RESULT để trả về cho Client
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            } else {
                String[] responseData = {"false", "Lỗi CSDL: Không thể cập nhật mật khẩu"};
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            }
            sendToClient(response);
        } catch (Exception e) {
            logger.error("SERVER LỖI: {}", e.getMessage(), e);
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
                    logger.warn("SERVER: Mã OTP đã hết hạn cho email {}", email);
                    response = new Message("FORGOT_PASSWORD_RESULT", new String[] {"false",
                            "Mã OTP đã hết hạn (quá 5 phút). Vui lòng gửi lại mã mới!"});
                }
                // 2. KIỂM TRA TÍNH CHÍNH XÁC
                else if (storedOtpData.code.equals(clientOtp)) {
                    otpStorage.remove(email); // Dùng xong cũng xé nháp (Bảo mật 1 lần)
                    logger.info("SERVER: Mã OTP hợp lệ cho email {}", email);
                    response = new Message("FORGOT_PASSWORD_RESULT",
                            new String[] {"true", "Xác thực OTP thành công!"});
                } // 3. NHẬP SAI
                else {
                    logger.warn("SERVER: Mã OTP sai cho email {}", email);
                    response = new Message("FORGOT_PASSWORD_RESULT",
                            new String[] {"false", "Mã xác thực OTP không chính xác!"});

                }
            } else {
                logger.warn("SERVER: Mã OTP sai hoặc đã hết hạn cho email {}", email);
                String[] responseData =
                        {"false", "Mã xác thực OTP không chính xác hoặc đã hết hạn!"};
                response = new Message("FORGOT_PASSWORD_RESULT", responseData);
            }

            // Gửi kết quả về lại Client
            sendToClient(response);

        } catch (Exception e) {
            logger.error("Lỗi khi xác thực OTP: {}", e.getMessage(), e);
            sendToClient(Message.error("Lỗi hệ thống khi xác thực OTP."));
        }
    }
    //==================================================================================================================
    // --------------------------------------NHÓM NẠP/RÚT TIỀN----------------------------------------------------------
    //=================================================================================================================

    // Nhận yêu cầu nạp tiền, kiểm tra tính hợp lệ của số tiền và người nạp, sau đó gọi xuống Database để cộng tiền, rồi trả kết quả về
    private void handleDepositRequest(Message msg) {
        try {
            // Giải nén payload: [userId, amount, content, bankName]
            Object[] payload = (Object[]) msg.getData();
            String userId = (String) payload[0];
            long amount = (Long) payload[1];

            // Nếu số tiền nạp nhỏ hơn hoặc bằng 0 thì xử lý báo lỗi
            if (amount <= 0) {
                logger.warn("SERVER SECURITY: Phát hiện amount không hợp lệ: {}", amount);
                sendToClient(new Message("DEPOSIT_RESULT", new Object[] {false, 0L}));
                return;
            }

            // Kiểm tra xem User này đã đăng nhập chưa, VÀ ID gửi lên có đúng là ID của người đang mượn luồng (Thread)
            // này không (Chống hacker truyền ID của người khác vào để hack tiền)
            if (currentUser == null || !currentUser.getId().equals(userId)) {
                logger.warn("SERVER SECURITY: userId không khớp session, từ chối nạp tiền.");
                sendToClient(new Message("DEPOSIT_RESULT", new Object[] {false, 0L}));
                return;
            }
            // Gọi hàm Repository
            userRepository.updateBalance(userId, amount);
            User updatedUser = userRepository.findById(userId);


            Message response;
            if (updatedUser != null) {
                logger.info("SERVER: Nạp thành công {} cho user {}", amount, userId);
                Object[] responseData = {true, updatedUser.getBalance()};
                response = new Message("DEPOSIT_RESULT", responseData);
            } else {
                Object[] responseData = {false, 0L};
                response = new Message("DEPOSIT_RESULT", responseData);
            }
            sendToClient(response);
        } catch (Exception e) {
            logger.error("SERVER LỖI: Xử lý giao dịch nạp tiền thất bại: {}", e.getMessage(), e);
            Object[] responseData = {false, 0L};
            sendToClient(new Message("ERROR", responseData));
        }
    }

    // =================================================================================================================
    // -------------------------------------NHOM ADMIN QUẢN LÝ NGƯỜI DÙNG-----------------------------------------------
    // =================================================================================================================
    private void handleGetAllUsers() {
        // Chặn ngay nếu không phải Admin
        if (!isAdminClient()) {
            sendToClient(new Message("ERROR", "Yêu cầu quyền Admin để xem danh sách user!"));
            return;
        }

        try {
            List<User> allUsers = userRepository.findAll();
            // Gửi về đúng type "ALL_USERS_RESULT" mà AuctionClient đang lắng nghe
            sendToClient(new Message("ALL_USERS_RESULT", (java.io.Serializable) allUsers));

            // Controller hiện tại lưu trạng thái ở cache riêng, nên gửi trạng thái từng user
            // ngay sau danh sách để bảng hiển thị đúng khi Admin mở lại màn hình.
            for (User user : allUsers) {
                sendToClient(createUserStatusChangedMessage(user.getId(),
                        userRepository.isUserActive(user.getId())));
            }
            logger.info("SERVER: Đã gửi {} users về Admin.", allUsers.size());
        } catch (Exception e) {
            logger.error("handleGetAllUsers lỗi: {}", e.getMessage(), e);
            sendToClient(new Message("ERROR", "Không thể lấy danh sách người dùng."));
        }
    }

    // ============================================================
    // ADMIN: Toggle trạng thái khóa/mở khóa tài khoản
    // ============================================================
    private void handleToggleUserStatus(Message msg) {
        if (!isAdminClient()) {
            sendToClient(new Message("ERROR", "Yêu cầu quyền Admin!"));
            return;
        }

        String targetUserId = (String) msg.getData();
        if (targetUserId == null || targetUserId.isBlank()) {
            sendToClient(new Message("ERROR", "userId không hợp lệ."));
            return;
        }

        // Chặn Admin tự khóa chính mình — tránh tình huống hệ thống mất quyền quản trị
        if (targetUserId.equals(currentUser.getId())) {
            sendToClient(new Message("ERROR", "Bạn không thể khóa chính tài khoản của mình!"));
            return;
        }

        try {
            // Bước 1: Kéo Model từ DB lên bộ nhớ RAM
            User targetUser = userRepository.findById(targetUserId);
            if (targetUser == null) {
                sendToClient(new Message("ERROR", "Không tìm thấy người dùng này trong hệ thống."));
                return;
            }
            // Bước 2: Ra lệnh cho Model tự đảo trạng thái (Xử lý 100% trên RAM)
            if (targetUser.isActive()) {
                targetUser.lockAccount(); // Model tự biết phải làm gì
            } else {
                targetUser.unlockAccount();
            }
            // Bước 3: Đẩy nguyên Model đã cập nhật cất lại vào DB
            userRepository.update(targetUser);
            // Broadcast realtime tới TẤT CẢ Admin đang online
            server.broadcastToAdmins(createUserStatusChangedMessage(targetUserId, targetUser.isActive()));

            System.out.println("SERVER: Admin " + currentUser.getUserName()
                    + (targetUser.isActive() ? " đã MỞ KHÓA" : " đã KHÓA")
                    + " tài khoản user: " + targetUserId);

        } catch (Exception e) {
            logger.error("handleToggleUserStatus lỗi: {}", e.getMessage(), e);
            sendToClient(new Message("ERROR", "Lỗi hệ thống khi thay đổi trạng thái user."));
        }
    }

    // ============================================================
    // ADMIN: Xóa vĩnh viễn một user
    // ============================================================
    private void handleDeleteUser(Message msg) {
        if (!isAdminClient()) {
            sendToClient(new Message("ERROR", "Yêu cầu quyền Admin!"));
            return;
        }

        String targetUserId = (String) msg.getData();
        if (targetUserId == null || targetUserId.isBlank()) {
            sendToClient(new Message("ERROR", "userId không hợp lệ."));
            return;
        }

        // Chặn Admin tự xóa chính mình
        if (targetUserId.equals(currentUser.getId())) {
            sendToClient(new Message("ERROR", "Bạn không thể xóa chính tài khoản của mình!"));
            return;
        }

        try {
            boolean isSuccess = userRepository.deleteUser(targetUserId);

            if (isSuccess) {
                // Broadcast tới tất cả Admin: reload lại danh sách user
                // Cách đơn giản nhất: gửi lại toàn bộ danh sách sau khi xóa
                List<User> updatedList = userRepository.findAll();
                server.broadcastToAdmins(
                        new Message("ALL_USERS_RESULT", (java.io.Serializable) updatedList));

                logger.info("SERVER: Admin {} đã XÓA user: {}", currentUser.getUserName(),
                        targetUserId);
            } else {
                sendToClient(new Message("ERROR", "Xóa user thất bại, vui lòng thử lại."));
            }

        } catch (Exception e) {
            logger.error("handleDeleteUser lỗi: {}", e.getMessage(), e);
            sendToClient(new Message("ERROR", "Lỗi hệ thống khi xóa user."));
        }
    }

    private Message createUserStatusChangedMessage(String userId, boolean isActive) {
        Map<String, Object> statusPayload = new HashMap<>();
        statusPayload.put("userId", userId);
        statusPayload.put("isActive", isActive);
        return new Message("USER_STATUS_CHANGED", statusPayload);
    }
    // =================================================================================================================
    // ---------------------------------------NHÓM SẢN PHẨM & ĐẤU GIÁ --------------------------------------------------
    // =================================================================================================================
    private void handleGetAllAuctions() {
        List<Auction> auctions = auctionRepository.findAllActiveAuctions();
        sendToClient(Message.returnAllAuctions(auctions));
    }


    private void handleGetLiveAuctions() {
        // Gọi DB lấy các phiên có status = PENDING hoặc OPEN
        List<Auction> liveList = auctionRepository.findLiveAuctions();
        sendToClient(new Message("LIVE_AUCTIONS_RESULT", liveList));
    }
    private void handleGetPendingAuctions() {
        if (!isAdminClient()) {
            sendToClient(new Message("ERROR", "Yêu cầu quyền Admin!"));
            return;
        }

        List<Auction> pendingList = auctionRepository.findAuctionsByStatus(AuctionStatus.PENDING);
        sendToClient(new Message("PENDING_AUCTIONS_RESULT", pendingList));
    }
    private void handleGetMyAuctions(Message msg) {
        String userIdForMyAuctions = (String) msg.getData();
        logger.info("SERVER: Đang truy vấn danh sách đấu giá cho user: {}", userIdForMyAuctions);

        try {
            List<MyAuctionDTO> myAuctionList = auctionRepository.getMyAuctions(userIdForMyAuctions);
            Message responseMyAuctions = new Message("MY_AUCTIONS_RESULT", myAuctionList);
            this.sendToClient(responseMyAuctions);
            logger.info("SERVER: Đã gửi {} phiên đấu giá.", myAuctionList.size());
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý GET_MY_AUCTIONS: {}", e.getMessage(), e);
            this.sendToClient(new Message("ERROR", "Không thể lấy danh sách đấu giá"));
        }
    }
    // Lấy tất cả các phiên do user đăng bán
    private void handleGetSellerAuctions(Message msg) {
        // Lấy dữ liệu bến trong gói tin ép thành string và gán biến sellerId
        String sellerId = (String) msg.getData();
        logger.info("SERVER: Đang truy vấn danh sách bán hàng cho user");
        try {
            List<Auction> sellerList = auctionRepository.findBySellerId(sellerId);
            // Tạo một gói tin Message mới chứa nhãn và mang theo danh sách sellerList, sau đó đẩy qua ống mạng (Socket) về cho Client.
            sendToClient(new Message("SELLER_AUCTIONS_RESULT", sellerList));
        } catch (Exception e){
            // Nếu có lỗi, tạo một gói tin báo lỗi "ERROR" và gửi về cho Client để giao diện không bị treo.
            sendToClient(new Message("ERROR", "Không thể lấy danh sách bán hàng"));
        }
    }
    // =================================================================================================================
    // ---------------------------------------NHÓM BIDDING & AUTO-BID---------------------------------------------------
    // =================================================================================================================
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
            // ==========================================
            User bidder = userRepository.findById(userId);

            // 1. Kiểm tra tài khoản đã bị xóa khỏi DB
            if (bidder == null) {
                sendToClient(Message.bidFail("Tài khoản không tồn tại hoặc đã bị xóa khỏi hệ thống!"));
                return; // Dừng tiến trình ngay lập tức
            }

            // 2. Kiểm tra tài khoản đang bị khóa
            if (!bidder.isActive()) {
                sendToClient(Message.bidFail("Tài khoản của bạn đã bị khóa! Vui lòng liên hệ Admin."));
                return; // Dừng tiến trình ngay lập tức
            }
            // [THÊM MỚI]: Lấy ID người dẫn đầu CŨ trước khi thực hiện giao dịch
            Auction currentAuction = auctionRepository.findById(auctionId);
            String oldWinnerId = (currentAuction != null && currentAuction.getWinner() != null)
                    ? currentAuction.getWinner().getId()
                    : null;
            boolean isSuccess =
                    bidRepository.executeBidTransaction(userId, auctionId, bidAmount, newBidId);
            if (isSuccess) {
                String bidderFullName =
                        (this.currentUser.getFullName() != null) ? this.currentUser.getFullName()
                                : msg.getUsername();
                server.broadcastToAll(Message.updatePrice(bidderFullName, auctionId, bidAmount));
                sendToClient(Message.bidSuccess());
                // ===== [BỔ SUNG TỪ ĐÂY]: Ép giao diện Sidebar cập nhật số dư =====
                User updatedUser = userRepository.findById(userId);
                if (updatedUser != null) {
                    // Mượn tạm gói tin DEPOSIT_RESULT (vì Client đã có sẵn logic cập nhật số dư cho
                    // gói này)
                    sendToClient(new Message("DEPOSIT_RESULT",
                            new Object[] {true, updatedUser.getBalance()}));
                }
                // Hoàn tiền cho người thua
                notifyRefundToOldWinner(oldWinnerId, userId);
                // [THÊM MỚI] Kích hoạt Auto-bid ngay sau khi Bid thủ công thành công
                // Chạy trên luồng riêng để không làm chậm phản hồi cho người vừa bid
                triggerAutoBid(auctionId, userId, bidAmount);
            } else {
                // ===== [BỔ SUNG NHÁNH ELSE Ở ĐÂY] =====
                // Nhánh này bắt các trường hợp Repository return false (Thường do lỗi kỹ thuật của
                // Database)
                logger.error(
                        "SERVER LỖI: Giao dịch từ chối không rõ nguyên nhân (DB return false) cho user {}",
                        userId);
                sendToClient(Message.bidFail(
                        "Giao dịch thất bại do lỗi máy chủ cơ sở dữ liệu. Vui lòng thử lại sau!"));
            }
        } catch (InvalidBidException | AuthenticationException | AuctionClosedException e) {
            // BẮT LỖI NGHIỆP VỤ: Gửi chính xác thông báo lỗi về cho người dùng
            sendToClient(Message.bidFail(e.getMessage()));
        } catch (Exception e) {
            // BẮT LỖI HỆ THỐNG (Lỗi Database, NullPointer, v.v.): Tránh làm sập Server
            logger.error("SERVER: Lỗi hệ thống khi xử lý BID", e);
            sendToClient(Message.error("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau!"));
        }
    }
    private void handleGetBidHistory(Message msg) {
        // BUG FIX: Dùng getAuctionId() thay vì getData() để nhất quán
        // (AuctionClient.getBidHistory đã gửi auctionId vào field auctionId của Message)
        String auctionId = msg.getAuctionId();
        List<BidTransaction> history = bidRepository.getBidsByAuctionId(auctionId);
        sendToClient(Message.returnBidHistory(history));
    }
    private void handleSetupAutoBid(Message msg) {
        // Kiểm tra trạng thái xem có đăng nhập chưa
        if (currentUser == null) {
            sendToClient(Message.autoBidFail("Bạn cần đăng nhập trước!"));
            return;
        }
        Object[] data = (Object[]) msg.getData();
        String auctionId = (String) data[0];
        long maxPrice = (Long) data[1];
        long stepPrice = (Long) data[2];
        // Người dùng gửi maxPrice=0 nghĩa là muốn TẮT auto-bid
        if (maxPrice == 0) {
            ((AutoBidRepositoryImpl) autoBidRepository)
                    .deactivateByUserAndAuction(currentUser.getId(), auctionId);
            sendToClient(Message.autoBidSuccess());
            return;
        }

        AutoBidConfig config = new AutoBidConfig();
        config.setId(UUID.randomUUID().toString());
        config.setMaxLimit(maxPrice);
        config.setIncrement(stepPrice);
        // Tạo object bider
        RegularUser bidder = new RegularUser();
        // truyền vào id
        bidder.setId(currentUser.getId());
        // Truyền id người đặt bid cho autobid
        config.setBidder(bidder);
        // Truyền các thuộc tính cho Auction
        Auction auction = new Auction();
        auction.setId(auctionId);
        config.setAuction(auction);
        config.setAuction(auction);
        // Tiến hanh lưu autobid
        boolean ok = autoBidRepository.save(config);
        // Lưu thành công thì gửi tin nhắn thành công
        sendToClient(ok ? Message.autoBidSuccess()
                : Message.autoBidFail("Không thể lưu cấu hình Auto-bid!"));
        if (ok) {
            try {
                // 1. Lấy thông tin phiên đấu giá mới nhất từ DB để xem ai đang dẫn đầu
                Auction currentAuction = auctionRepository.findById(auctionId);
                if (currentAuction != null) {
                    String currentWinnerId =
                            currentAuction.getWinner() != null ? currentAuction.getWinner().getId()
                                    : null;
                    long currentPrice = currentAuction.getHighestBid();

                    // 2. Nếu người vừa cài Auto-bid KHÔNG PHẢI là người đang dẫn đầu
                    if (!currentUser.getId().equals(currentWinnerId)) {
                        logger.info("SERVER: Khởi động nóng Auto-bid cho user {}",
                                currentUser.getId());
                        // Đánh lừa hệ thống rằng người dẫn đầu cũ vừa bid,
                        // để hàm triggerAutoBid chạy và tự động đè giá của người dẫn đầu cũ.
                        triggerAutoBid(auctionId, currentWinnerId, currentPrice);
                    }
                }
            } catch (Exception e) {
                logger.error("SERVER: Lỗi khi khởi động nóng Auto-bid", e);
            }
        }
    }

    /**
     * Kích hoạt chuỗi Auto-bid sau một lượt bid thủ công thành công.
     *
     * THIẾT KẾ AN TOÀN: - Chạy trên Virtual Thread riêng → không block luồng xử lý chính - Mỗi vòng
     * lặp tái sử dụng executeBidTransaction() đã có Lock + Transaction → đảm bảo atomic, tránh Race
     * Condition và Deadlock - Khi auto-bid thành công, lại gọi đệ quy triggerAutoBid() để xử lý
     * trường hợp nhiều người cùng bật Auto-bid (chuỗi phản ứng)
     */
    private void triggerAutoBid(String auctionId, String triggerUserId, long currentPrice) {
        Thread.ofVirtual().name("auto-bid-" + auctionId).start(() -> {
            try {
                // Lấy danh sách Auto-bid còn hoạt động, loại trừ người vừa bid
                List<AutoBidConfig> configs = ((AutoBidRepositoryImpl) autoBidRepository)
                        .findActiveByAuctionId(auctionId, triggerUserId);

                if (configs.isEmpty())
                    return;
                // Chỉ xử lý người đầu tiên (max_price cao nhất) để tránh vòng lặp vô hạn
                // Vòng tiếp theo sẽ tự kích hoạt khi bid này thành công
                AutoBidConfig config = configs.get(0);
                String autoUserId = config.getBidder().getId();
                long autoBidAmount = currentPrice + config.getIncrement();

                // Kiểm tra không vượt giới hạn
                if (autoBidAmount > config.getMaxLimit()) {
                    logger.info("AUTO-BID: User {} đã chạm giới hạn {}, tắt auto-bid.", autoUserId,
                            config.getMaxLimit());
                    ((AutoBidRepositoryImpl) autoBidRepository)
                            .deactivateByUserAndAuction(autoUserId, auctionId);
                    // Thông báo cho Client biết Auto-bid của họ đã bị ngắt
                    String reason = "Đã vượt ngưỡng tối đa (" + config.getMaxLimit()
                            + " VNĐ). Auto-bid đã tự động tắt.";
                    Object[] stopPayload = {auctionId, autoUserId, reason};
                    server.broadcastToAll(
                            new Message("AUTO_BID_STOPPED", "System", auctionId, 0, stopPayload));
                    return;
                }
                // Lấy id người dẫn đầu cũ
                Auction currentAuction = auctionRepository.findById(auctionId);
                String oldWinnerId = (currentAuction != null && currentAuction.getWinner() != null)
                        ? currentAuction.getWinner().getId()
                        : null;

                // Tái sử dụng executeBidTransaction() — đã có Lock + Transaction bên trong
                String autoBidId = UUID.randomUUID().toString();
                boolean success = bidRepository.executeBidTransaction(autoUserId, auctionId,
                        autoBidAmount, autoBidId);

                if (success) {
                    logger.info("AUTO-BID: User {} tự động bid {}", autoUserId, autoBidAmount);

                    User autoUser = userRepository.findById(autoUserId);
                    String displayName = (autoUser != null) ? autoUser.getFullName() : "Ẩn danh";
                    // Broadcast giá mới cho Clienta
                    server.broadcastToAll(
                            Message.updatePrice(displayName, auctionId, autoBidAmount));
                    // [THÊM MỚI]: Báo tin hoàn tiền cho máy của người vừa bị vượt giá
                    notifyRefundToOldWinner(oldWinnerId, autoUserId);

                    // Đệ quy: Kích hoạt Auto-bid tiếp theo nếu có người khác cũng bật
                    // Thêm delay nhỏ 500ms để tránh flood message
                    Thread.sleep(500);
                    triggerAutoBid(auctionId, autoUserId, autoBidAmount);
                }

            } catch (InvalidBidException | AuctionClosedException e) {
                logger.warn("AUTO-BID: Dừng vì nghiệp vụ - {}", e.getMessage());
            } catch (Exception e) {
                logger.error("AUTO-BID: Lỗi không xác định", e);
            }
        });
    }
    // Hoàn tiền cho người thua cuộc
    private void notifyRefundToOldWinner(String oldWinnerId, String newWinnerId) {
        // Nếu có người dẫn đầu cũ, và người đó không phải là người vừa bid
        if (oldWinnerId != null && !oldWinnerId.equals(newWinnerId)) {

            // Tìm lại số dư mới nhất của người bị vượt dưới DB
            User oldUserDb = userRepository.findById(oldWinnerId);

            if (oldUserDb != null) {
                // Đóng gói tin nhắn cập nhật ví
                Message refundMsg =
                        new Message("DEPOSIT_RESULT", new Object[] {true, oldUserDb.getBalance()});

                // Nhờ Server gửi đích danh cho người đó bằng hàm vừa tạo
                server.sendToUser(oldWinnerId, refundMsg);

                logger.info("SERVER: Đã báo tin nhắn số dư cho người bị vượt giá: {}", oldWinnerId);
            }
        }
    }
    // =================================================================================================================
    // -----------------------------------NHOM THEO DÕI PHIÊN-----------------------------------------------------------
    // =================================================================================================================
    private void handleWatchItem(Message msg) {
        server.watchAuction(msg.getAuctionId(), this);
    }

    private void handleUnwatchItem(Message msg) {
        server.unwatchAuction(msg.getAuctionId(), this);
    }
    // =================================================================================================================
    // ---------------------------------QUẢN LÝ PHIÊN(ADMIN)------------------------------------------------------------
    // =================================================================================================================
    private void handleCreateAuctionRequest(Message msg) {
        if (!isSellerClient()) {
            sendToClient(new Message("CREATE_AUCTION_RESULT", new String[] {
                    "false", "Lỗi phân quyền: Bạn không có đặc quyền đăng bán sản phẩm!"}));
            return;
        }
        try {
            Object[] payload = (Object[]) msg.getData();
            Items item = (Items) payload[0];
            Auction auction = (Auction) payload[1];
            @SuppressWarnings("unchecked")
            Map<String, byte[]> imageDataMap = (Map<String, byte[]>) payload[2];

            // Tạo thư mục lưu ảnh nếu chưa có (ngoài JAR, bền vững khi restart)
            File dir = new File(IMAGE_DIR);
            if (!dir.exists())
                dir.mkdirs();

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
                    item.addImage(new ItemImage(UUID.randomUUID().toString(), fileName, // ← CHỈ LƯU
                            // TÊN FILE:
                            // "item_abc123_0.jpg"
                            item.getId()));
                }
            }
            // Gọi server lưu DB
            AuctionService auctionService = new AuctionService();
            boolean isSuccess = auctionService.createAuctionListing(item, auction);
            if (isSuccess) {
                server.broadcastToAdmins(new Message("NEW_PENDING_AUCTION_ALERT", auction));
            }
            // Trả về kết quả Client
            sendToClient(new Message("CREATE_AUCTION_RESULT", new String[] {
                    String.valueOf(isSuccess),
                    isSuccess ? "Tạo phiên đấu giá thành công." : "Lưu phiên đấu giá thất bại."}));

        } catch (Exception e) {
            logger.error("SERVER LỖI: Xử lý ảnh thất bại", e);
            sendToClient(new Message("CREATE_AUCTION_RESULT",
                    new String[] {"false", "Lỗi server khi tạo phiên đấu giá."}));
        }
    }
    private void handleApproveAuction(Message msg) {
        if (!isAdminClient()) {
            sendToClient(
                    new Message("APPROVE_RESULT", new Object[] {false, "Yêu cầu quyền Admin!"}));
            return;
        }

        String auctionId = (String) msg.getData();
        Auction auction = auctionRepository.findById(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.PENDING) {
            sendToClient(new Message("APPROVE_RESULT",
                    new Object[] {false, "Phiên đấu giá không hợp lệ hoặc đã được xử lý!"}));
            return;
        }

        auction.setApprovedBy(currentUser.getId());
        if (auction.getStartTime() != null
                && LocalDateTime.now().isBefore(auction.getStartTime())) {
            auction.setStatus(AuctionStatus.UP_COMING);
        } else {
            auction.setStatus(AuctionStatus.OPEN);
        }
        boolean isSuccess = auctionRepository.updateAuctionStatus(auction);
        if (isSuccess) {
            Auction updatedAuction = auctionRepository.findById(auctionId);
            server.broadcastToAll(new Message("AUCTION_APPROVED_ALERT", updatedAuction));
            // THÊM MỚI: Nếu phiên lên sàn ngay (OPEN), broadcast thêm AUCTION_STARTED
            // để LiveAuctionContent tự thêm thẻ mà không cần F5
            if (updatedAuction != null && updatedAuction.getStatus() == AuctionStatus.OPEN) {
                server.broadcastToAll(new Message("AUCTION_STARTED", updatedAuction));
            }
        }
        sendToClient(new Message("APPROVE_RESULT",
                new Object[] {isSuccess, isSuccess ? "Đã duyệt sản phẩm." : "Duyệt thất bại!"}));
    }
    private void handleRejectAuction(Message msg) {
        if (!isAdminClient()) {
            sendToClient(
                    new Message("REJECT_RESULT", new Object[] {false, "Yêu cầu quyền Admin!"}));
            return;
        }

        String auctionId = (String) msg.getData();
        Auction auction = auctionRepository.findById(auctionId);
        if (auction == null || auction.getStatus() != AuctionStatus.PENDING) {
            sendToClient(new Message("REJECT_RESULT",
                    new Object[] {false, "Phiên đấu giá không hợp lệ hoặc đã được xử lý!"}));
            return;
        }

        auction.setStatus(AuctionStatus.CANCELED);
        boolean isSuccess = auctionRepository.updateAuctionStatus(auction);
        sendToClient(new Message("REJECT_RESULT", new Object[] {isSuccess,
                isSuccess ? "Đã từ chối sản phẩm." : "Từ chối thất bại!"}));
    }

    private void handleForceCancelAuction(Message msg) {
        if (!isAdminClient()) return; // Bảo mật: chặn user thường
        String auctionId = (String) msg.getData();

        // 1. Gọi Database đổi trạng thái
        boolean success = auctionRepository.cancelAuction(auctionId);

        if (success) {
            // 2. Logic hoàn tiền: Trả lại tiền cho người đang giữ Top 1 (nếu có)
            // ... (Sử dụng Model hoặc Repository tùy kiến trúc hiện tại của em)

            // 3. Thông báo cho toàn bộ mạng lưới là phiên này đã bị hủy
            server.broadcastToAll(new Message("AUCTION_CANCELLED", auctionId));
            // Gửi lại danh sách mới cho Admin cập nhật màn hình
            handleGetLiveAuctions();
        }
    }
    // =================================================================================================================
    // ---------------------------TIỆN ÍCH QUẢN LÝ----------------------------------------------------------------------
    // =================================================================================================================
    private void handleGetDashboardData() {
        if (isAdminClient()) {
            DashboardRepository dashRepo = new DashboardRepository();
            DashboardDataDTO dto = dashRepo.buildDashboardData(server.getOnlineUserCount());
            // Gửi DTO duy nhất — Controller tự unpack
            sendToClient(new Message("DASHBOARD_DATA_RESULT", dto));
            return;
        }

        // Giữ payload cũ cho Dashboard client thường đang dùng cùng message GET_DASHBOARD_DATA.
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
    // THÊM MỚI: Handler trả ảnh về Client dưới dạng byte[]
    // =========================================================================
    private void handleGetImage(Message msg) {
        String fileName = (String) msg.getData();
        // [REFACTOR] Guard: Validate tên file — chống Path Traversal
        if (!isValidImageFileName(fileName)) {
            logger.warn("SERVER SECURITY: Yêu cầu ảnh với tên file không hợp lệ: {}", fileName);
            sendToClient(new Message("IMAGE_RESULT", null));
            return;
        }
        File imageFile = new File(IMAGE_DIR + fileName);

        if (!imageFile.exists()) {
            sendToClient(new Message("IMAGE_RESULT", null));
            logger.warn("SERVER: Không tìm thấy ảnh: {}", fileName);
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            // Payload: [fileName, byte[]] để Client biết ảnh nào vừa về
            sendToClient(new Message("IMAGE_RESULT", new Object[] {fileName, imageBytes}));
        } catch (IOException e) {
            logger.error("SERVER: Lỗi đọc ảnh {}", fileName, e);
            sendToClient(new Message("IMAGE_RESULT", null));
        }
    }
    /**
     * Validate tên file chống Path Traversal. Chỉ cho phép: chữ cái, số, gạch dưới, gạch ngang, dấu
     * chấm. Từ chối: "../", "/", "\", ký tự đặc biệt.
     */
    private boolean isValidImageFileName(String fileName) {
        if (fileName == null || fileName.isBlank())
            return false;
        // Regex: chỉ cho phép a-z, A-Z, 0-9, _, -, và đúng 1 dấu chấm trước extension
        return fileName.matches("^[a-zA-Z0-9_\\-]+\\.(jpg|jpeg|png)$");
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
            logger.error("SERVER: Không thể gửi message tới client", e);
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
            logger.error("SERVER: Lỗi đóng socket", e);
        }
    }

    public boolean isAdminClient() {
        return currentUser != null && currentUser.getRoleName() != null
                && currentUser.getRoleName().contains(Role.ADMIN.name());
    }
    public boolean isSellerClient() {
        return currentUser != null && currentUser.getRoleName() != null
                && currentUser.getRoleName().contains(Role.SELLER.name());
    }

    public User getCurrentUser() {
        return this.currentUser;
    }
}

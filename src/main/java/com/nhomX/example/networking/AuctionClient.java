package com.nhomX.example.networking;

import com.nhomX.example.manager.SessionManager;
import com.nhomX.example.model.*;
import com.nhomX.example.manager.AuctionManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class AuctionClient {

    private String username;
    //Đưa socket lên làm thuộc tính class để chống Leak
    private Socket socket;
    //Thêm volatile để chống Race Condition khi đọc/ghi đa luồng:
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;

    private volatile ServerEventListener listener;

    // Cung cấp hàm để các Controller sử dụng:
    public void setServerEventListener(ServerEventListener listener) {
        this.listener = listener;
    }

    public AuctionClient(String username) {
        this.username = username;
    }

    public void connect(String host, int port) {
        try {
            this.socket = new Socket(host, port);
            // Khởi tạo luồng (Out trước, In sau chống Deadlock)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // LUỒNG NGẦM: Luôn lắng nghe cập nhật từ Server để không làm treo UI
            Thread listenerThread = new Thread(this::listenToServer,"client-listener" );
            listenerThread.setDaemon(true); // Tự tắt khi ứng dụng chính tắt
            listenerThread.start();
            System.out.println("CLIENT: Đã kết nối tới " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("CLIENT: Không thể kết nối tới Server.");
        }
    }
    // Dọn dẹp tài nguyên chống leak:
    public void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("CLIENT: Đã đóng kết nối an toàn.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gửi yêu cầu đặt giá lên Server
    public void placeBid(String userId,String auctionId , long bidAmount) {
        Object[] bidData = {userId, auctionId, bidAmount};
        Message bid = new Message("BID", username, auctionId, bidAmount, bidData);
        sendToServer(bid);
    }
    /** Yêu cầu Server trả về danh sách phiên đang mở. */
    public void requestAllAuctions() {
        sendToServer(new Message("GET_ALL_AUCTIONS"));
    }

    /** Gửi yêu cầu đăng nhập. Password phải đã hash SHA-256 trước khi gọi. */
    public void login(String username, String passwordHash) {
        String[] data = {username, passwordHash};
        sendToServer(new Message("LOGIN", username, null, 0, data));
    }

    /** Gửi yêu cầu đăng ký tài khoản. Password phải đã hash SHA-256 trước khi gọi. */
    public void register(String username, String passwordHash, String fullName) {
        Object[] data = {username, passwordHash, fullName, 0L};
        sendToServer(new Message("REGISTER", username, null, 0, data));
    }

    /** Thiết lập auto-bid cho một phiên. */
    public void setupAutoBid(String auctionId, long maxLimit, long increment) {
        Object[] data = {auctionId, maxLimit, increment};
        sendToServer(new Message("SETUP_AUTO_BID", username, auctionId, 0, data));
    }

    // Lắng nghe các UPDATE từ Server gửi về (Realtime)
    private void listenToServer() {
        try {
            Message msgFromServer;
            while ((msgFromServer = (Message) in.readObject()) != null) {
                handleServerMessage(msgFromServer);
            }
        } catch (Exception e) {
            // Khi Server sập, đứt mạng, luồng đọc object sẽ văng Exception nhảy vào đây
            System.err.println("CLIENT: Mất kết nối: " + e.getMessage());
        }
    }
    //Thêm synchronized(out) để thread-safe khi nhiều luồng gửi cùng lúc.
    // Hàm mới: Gửi bất kỳ Message nào lên Server (dùng cho Login, Register...)
    public void sendToServer(Message msg) {
        if (out == null) {
            System.err.println("CLIENT: Chưa kết nối tới Server!");
            return;
        }
        try {
            synchronized (out) {
                out.writeObject(msg);
                out.flush();
                // [QUAN TRỌNG] Chống rò rỉ bộ nhớ (Memory Leak) khi gửi Object liên tục
                out.reset();
            }
        } catch (IOException e) {
            System.err.println("CLIENT: Lỗi gửi message – " + e.getMessage());
        }
    }

    //Gọi khi người dùng MỞ giao diện chi tiết món hàng
    public void watchAuction(String auctionId) {
        sendToServer(new Message("WATCH_ITEM", username, auctionId, 0));
        System.out.println("CLIENT: Đang theo dõi phiên " + auctionId);
    }

    //Gọi khi người dùng ĐÓNG/THOÁT giao diện chi tiết món hàng
    public void unwatchAuction(String auctionId) {
        // Gửi tin nhắn loại "UNWATCH" lên Server
        sendToServer(new Message("UNWATCH_ITEM", username, auctionId, 0));
        System.out.println("CLIENT: Đã hủy theo dõi phiên " + auctionId);
    }
    public void getBidHistory(String auctionId) {
        sendToServer(new Message("GET_BID_HISTORY",this.username, auctionId, 0, null));
    }
    /**
     * [FIX] Tách xử lý từng loại message ra hàm riêng thay vì một khối if-else khổng lồ.
     * Dễ đọc và dễ thêm loại message mới.
     */
    private void handleServerMessage(Message msg) {
        String type = msg.getType();
        switch (type) {

            case "UPDATE_PRICE":
                //Cập nhật Nguồn Sự Thật trên RAM ngay lập tức
                AuctionManager.getInstance().updateAuctionPrice(
                        msg.getAuctionId(),
                        msg.getAmount(),
                        null,
                        msg.getUsername()
                );
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onHighestBidUpdated(msg.getAuctionId(), msg.getAmount(), msg.getUsername());
                    }
                });
                break;

            case "AUCTION_CLOSED":
                // [THÊM MỚI] Xử lý sự kiện phiên đóng
                String winnerId = msg.getData() != null ? (String) msg.getData() : null;
                // [REFACTOR]: Đóng băng dữ liệu trong Bể chứa RAM
                AuctionManager.getInstance().closeAuctionInCache(msg.getAuctionId(), winnerId);
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onAuctionClosed(msg.getAuctionId(), winnerId);
                    }
                });
                break;

            case "BID_SUCCESS":
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onBidResult(true, (String) msg.getData());
                    }
                });
                break;

            case "BID_FAIL":
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onBidResult(false, (String) msg.getData());
                    }
                });
                break;

            case "LOGIN_SUCCESS":
                User loggedInUser = (User) msg.getData();
                // Lưu session ngay khi nhận LOGIN_SUCCESS
                SessionManager.getInstance().login(loggedInUser);
                // [FIX] Cập nhật username trong client theo user thực tế từ DB
                this.username = loggedInUser.getUserName();
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onLoginResult(true, "Đăng nhập thành công!", loggedInUser);
                    }
                });
                break;

            case "LOGIN_FAIL":
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onLoginResult(false,
                                msg.getData() != null ? (String) msg.getData()
                                        : "Sai tên đăng nhập hoặc mật khẩu!", null);
                    }
                });
                break;
            case "SHOW_OTP_DIALOG":
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onShowOtpDialog();
                    }
                });
                break;

            case "REGISTER_SUCCESS":
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onRegisterResult(true,
                                "Đăng ký thành công! Vui lòng đăng nhập.");
                    }
                });
                break;

            case "REGISTER_FAIL":
                String errMsg = msg.getData() != null ? (String) msg.getData()
                        : "Đăng ký thất bại!";
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onRegisterResult(false, errMsg);
                    }
                });
                break;

            case "RETURN_ALL_AUCTIONS":
                @SuppressWarnings("unchecked")
                List<Auction> auctions = (List<Auction>) msg.getData();
                // [REFACTOR]: Nạp toàn bộ kho dữ liệu vào Manager
                AuctionManager.getInstance().setAllAuctions(auctions);
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onAuctionsReceived(auctions);
                    }
                });
                break;
            case "RETURN_BID_HISTORY":
                @SuppressWarnings("unchecked")
                List<BidTransaction> history = (List<BidTransaction>) msg.getData();
                runOnUiThread(() -> {
                    if (listener != null) {
                        // Cần thêm hàm này vào ServerEventListener.java trước
                        listener.onBidHistoryReceived(history);
                    }
                });
                break;

            case "ERROR":
                System.err.println("CLIENT nhận lỗi từ Server: " + msg.getData());
                break;

            case "DASHBOARD_DATA_RESULT":
                Object[] payload = (Object[]) msg.getData();
                Map<String, Integer> stats = (Map<String, Integer>) payload[0];
                List<Auction> endingSoon = (List<Auction>) payload[1];
                List<Auction> trending = (List<Auction>) payload[2];
                // [REFACTOR]: Sử dụng hàm Merge.
                    // Gộp cả 2 danh sách Trending và Ending Soon vào Cache để các món này luôn có giá tươi nhất,
                    // MÀ KHÔNG XÓA mất các món hàng khác đang có sẵn trên màn hình Live Auction.
                AuctionManager.getInstance().updateOrAddAuctions(trending);
                AuctionManager.getInstance().updateOrAddAuctions(endingSoon);
                if (listener != null){
                    listener.onDashboardDataReceived(stats, endingSoon, trending);
                }
                break;
            case "UPDATE_ONLINE_COUNT":
                int onlineCount = (Integer) msg.getData();
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onOnlineCountUpdated(onlineCount);
                    }
                });
                break;

            case "MY_AUCTIONS_RESULT":
                List<com.nhomX.example.model.MyAuctionDTO> myAuctionsList =
                        (List<MyAuctionDTO>) msg.getData();

                if (listener != null){
                    listener.onMyAuctionsReceived(myAuctionsList);
                }
                break;

            default:
                System.err.println("CLIENT: Loại message không xác định – " + type);
        }
    }
    /** Wrapper cho Platform.runLater – giúp code ngắn gọn hơn. */
    private void runOnUiThread(Runnable action) {
        javafx.application.Platform.runLater(action);
    }
    /** Thông báo mất kết nối qua listener (trên UI thread). */
    private void notifyConnectionLost(String reason) {
        runOnUiThread(() -> {
            if (listener != null) {
                listener.onConnectionLost(reason);
            }
        });
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}

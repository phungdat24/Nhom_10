package com.nhomX.example.networking;

import com.nhomX.example.controller.SessionManager;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class AuctionClient {

    private String username;
    //Đưa socket lên làm thuộc tính class để chống Leak
    private Socket socket;
    //Thêm volatile để chống Race Condition khi đọc/ghi đa luồng:
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;

    private ServerEventListener listener;

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
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true); // Tự tắt khi ứng dụng chính tắt
            listenerThread.start();

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
        try {
            // Đóng gói mảng data gửi lên ClientHandler
            Object[] bidData = new Object[]{userId, auctionId};
            Message bid = new Message("BID", username, auctionId, bidAmount, bidData);
            if (out != null) {
                // Thêm synchronized để an toàn luồng
                synchronized (out) {
                    out.writeObject(bid);
                    out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lắng nghe các UPDATE từ Server gửi về (Realtime)
    private void listenToServer() {
        try {
            Message msgFromServer;
            while ((msgFromServer = (Message) in.readObject()) != null) {
                String msgType = msgFromServer.getType();

                if ("UPDATE_PRICE".equals(msgType)) {
                    String auctionId = msgFromServer.getAuctionId();
                    long newPrice = msgFromServer.getAmount();

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onHighestBidUpdated(auctionId, newPrice);
                        });
                    }
                }else if ("BID_SUCCESS".equals(msgType) || "BID_FAIL".equals(msgType)) {
                    // Xử lý thông báo cá nhân khi đặt giá (từ ClientHandler gửi riêng)
                    String alertMsg = (String) msgFromServer.getData();
                    System.out.println("HỆ THỐNG: " + alertMsg);
                    // Ở đây em có thể gọi listener để popup thông báo lên UI
                }
                // Xử lý Đăng nhập thành công:
                else if ("LOGIN_SUCCESS".equals(msgType)) {

                    User loggedInUser = (User) msgFromServer.getData();

                    SessionManager.getInstance().login(loggedInUser);

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onLoginResult(true, "ĐĂNG NHẬP THÀNH CÔNG!", loggedInUser);
                        });
                    }
                }
                // Xử lý Đăng nhập thất bại
                else if ("LOGIN_FAIL".equals(msgType)) {

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onLoginResult(false, "ĐĂNG NHẬP THẤT BẠI!", null);
                        });
                    }
                } else if ("REGISTER_SUCCESS".equals(msgType)) {

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onRegisterResult(true,
                                    "ĐĂNG KÝ TÀI KHOẢN THÀNH CÔNG! VUI LÒNG ĐĂNG NHÂP!");
                        });
                    }
                } else if ("REGISTER_FAIL".equals(msgType)) {
                    String errorMsg =
                            msgFromServer.getData() != null ? (String) msgFromServer.getData()
                                    : "ĐĂNG KÝ THẤT BẠI DO LỖI HỆ THỐNG!";

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onRegisterResult(false, errorMsg);
                        });
                    }
                } else if ("RETURN_ALL_AUCTIONS".equals(msgType)) {
                    // Ép kiểu lấy danh sách Item ra
                    List<Auction> auctionList = (List<Auction>) msgFromServer.getData();

                    if (listener != null) {
                        javafx.application.Platform.runLater(() -> {
                            listener.onAuctionsReceived(auctionList);
                        });
                    }
                }
            }
        } catch (Exception e) {
            // Khi Server sập, đứt mạng, luồng đọc object sẽ văng Exception nhảy vào đây
            System.err.println("Mất kết nối: " + e.getMessage());

            if (listener != null) {
                javafx.application.Platform.runLater(() -> {
                    // Mượn tạm hàm onRegisterResult (hoặc onLoginResult) để báo lỗi bung popup
                    // Tốt nhất là sau này đẻ thêm hàm: listener.onConnectionError("Mất kết nối
                    // Server!");
                    listener.onRegisterResult(false, "Mất kết nối với Server! Vui lòng thử lại.");
                });
            }
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

    //Gọi khi người dùng MỞ giao diện chi tiết món hàng
    public void watchItem(String itemId) {
        // Gửi tin nhắn loại "WATCH" lên Server
        Message watchMsg = new Message("WATCH_ITEM", username, itemId, 0);
        sendToServer(watchMsg);
        System.out.println("CLIENT: Đã đăng ký theo dõi giá món " + itemId);
    }

    //Gọi khi người dùng ĐÓNG/THOÁT giao diện chi tiết món hàng
    public void unwatchItem(String itemId) {
        // Gửi tin nhắn loại "UNWATCH" lên Server
        Message unwatchMsg = new Message("UNWATCH_ITEM", username, itemId, 0);
        sendToServer(unwatchMsg);
        System.out.println("CLIENT: Đã hủy theo dõi giá món " + itemId);
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

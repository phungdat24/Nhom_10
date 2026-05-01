package com.nhomX.example.networking;

import com.nhomX.example.controller.MainDashBoardController;
import com.nhomX.example.controller.SessionManager;
import com.nhomX.example.model.Items;
import com.nhomX.example.model.User;
import com.nhomX.example.utils.AlertUtils;
import com.nhomX.example.utils.SceneSwitcher;

import java.io.*;
import java.net.Socket;
import java.util.List;

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

                    User loggedInUser =(User) msgFromServer.getData();

                    SessionManager.getInstance().login(loggedInUser);

                    // Dùng Platform.runLater để giao diện JavaFX không bị sập khi cập nhật từ luồng ngầm
                    javafx.application.Platform.runLater(() -> {
                        SceneSwitcher.switchScene("/com/nhomX/example/fxml/dashboard.fxml");

                        System.out.println("Giao diện: Đăng nhập thành công!");
                    });
                }
                // THÊM MỚI: Xử lý Đăng nhập thất bại
                else if ("LOGIN_FAIL".equals(msgType)) {
                    javafx.application.Platform.runLater(() -> {

                         AlertUtils.showError("Lỗi", "Sai thông tin đăng nhập!");
                        System.out.println("Giao diện: Đăng nhập thất bại!");
                    });
                }
                else if ("REGISTER_SUCCESS".equals(msgType)) {
                    javafx.application.Platform.runLater(() -> {
                        AlertUtils.showSuccess("Thành công", "Tài khoản đã được tạo. Vui lòng đăng nhập!");
                        SceneSwitcher.switchScene( "/com/nhomX/example/fxml/login.fxml");
                    });
                }
                else if ("REGISTER_FAIL".equals(msgType)) {
                    javafx.application.Platform.runLater(() -> {
                        // Thường là do trùng Email
                        AlertUtils.showError("Đăng ký thất bại", "Email này đã được sử dụng!");
                    });
                }
                else if ("RETURN_ALL_ITEMS".equals(msgType)) {
                    // Ép kiểu lấy danh sách Item ra
                    List<Items> itemList =(List<Items>) msgFromServer.getData();

                    javafx.application.Platform.runLater(() -> {
                        if(MainDashBoardController.instance != null){
                            MainDashBoardController.instance.updateProductUI(itemList);
                        }
                    });
                }
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
    // Bổ sung hàm 1: Gọi khi người dùng MỞ giao diện chi tiết món hàng
    public void watchItem(String itemId) {
        // Gửi tin nhắn loại "WATCH" lên Server
        Message watchMsg = new Message("WATCH", username, itemId, 0);
        sendToServer(watchMsg);
        System.out.println("CLIENT: Đã đăng ký theo dõi giá món " + itemId);
    }

    // Bổ sung hàm 2: Gọi khi người dùng ĐÓNG/THOÁT giao diện chi tiết món hàng
    public void unwatchItem(String itemId) {
        // Gửi tin nhắn loại "UNWATCH" lên Server
        Message unwatchMsg = new Message("UNWATCH", username, itemId, 0);
        sendToServer(unwatchMsg);
        System.out.println("CLIENT: Đã hủy theo dõi giá món " + itemId);
    }
}
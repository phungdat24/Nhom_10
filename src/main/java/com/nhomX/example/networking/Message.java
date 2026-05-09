package com.nhomX.example.networking;

import java.io.Serializable;

//Class này dùng để đóng gói dữ liệu truyền qua mạng.

public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo phiên bản class đồng nhất giữa 2 đầu

    private String type;
    private String username;
    private String auctionId;
    private long amount;
    private Object data;

    //Constructor siêu rỗng (Dùng khi chỉ cần gửi mỗi cái Lệnh, ví dụ: "GET_ALL_ITEMS")
    public Message(String type) {
        this.type = type;
    }

    public Message(String type, String username, String auctionId, long amount) {
        this.type = type;
        this.username = username;
        this.auctionId = auctionId;
        this.amount = amount;
    }
    //Constructor "Full Giáp" (Mới - Chở được mọi thứ cùng lúc)
    public Message(String type, String username, String auctionId, long amount, Object data) {
        this.type = type;
        this.username = username;
        this.auctionId = auctionId;
        this.amount = amount;
        this.data = data;
    }
    public Message(String type, Object data){
        this.type = type;
        this.data = data;
    }

    // Getter
    public String getType() {
        return type;
    }
    public String getUsername() {
        return username;
    }
    public String getAuctionId() {
        return auctionId;
    }
    public long getAmount() {
        return amount;
    }

    public Object getData() {
        return data;
    }
    // Set:


    public void setType(String type) {
        this.type = type;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        String dataStr = (data != null) ? data.getClass().getSimpleName() : "None";
        return String.format("[%s] %s -> Item: %s | Price: $%d | Data: %s",
                type,
                (username != null ? username : "N/A"),
                (auctionId != null ? auctionId : "N/A"),
                amount,
                dataStr);
    }

}
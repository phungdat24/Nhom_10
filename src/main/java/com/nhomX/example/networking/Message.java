package com.nhomX.example.networking;

import java.io.Serializable;

//Class này dùng để đóng gói dữ liệu truyền qua mạng.

public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo phiên bản class đồng nhất giữa 2 đầu

    private String type;
    private String username;
    private String itemId;
    private double amount;
    private Object data;

    public Message(String type, String username, String itemId, double amount) {
        this.type = type;
        this.username = username;
        this.itemId = itemId;
        this.amount = amount;
        this.data = data;
    }
    public Message(String type, Object data){
        this.type = type;
        this.data = data;
    }

    // Getter
    public String getType() { return type; }
    public String getUsername() { return username; }
    public String getItemId() { return itemId; }
    public double getAmount() { return amount; }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: $%.2f", type, username, itemId, amount);
    }

    public Object getData() {
        return data;
    }
}
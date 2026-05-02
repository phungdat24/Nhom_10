package com.nhomX.example.networking;

import com.nhomX.example.model.Items;
import com.nhomX.example.model.User;

import java.util.List;

public interface ServerEventListener {
    // Sự kiện đăng nhập
    default void onLoginResult (boolean isSuccess, String message, User userData) {};
    // Sự kiện cập nhật Realtime
    default void onPriceUpdated(String itemId, double newPrice) {};
    // Sự kiên nhận danh sách Items:
    default void onItemsReceived(List<Items> items) {};
    // Thêm sự kiện phản hồi Đăng ký
    default void onRegisterResult(boolean isSuccess, String message) {}
}

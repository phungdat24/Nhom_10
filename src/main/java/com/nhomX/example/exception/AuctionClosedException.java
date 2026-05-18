package com.nhomX.example.exception;
// Lỗi khi phiên đã đóng/hết hạn
public class AuctionClosedException extends RuntimeException{
    public AuctionClosedException(String message) {
        super(message);
    }
}

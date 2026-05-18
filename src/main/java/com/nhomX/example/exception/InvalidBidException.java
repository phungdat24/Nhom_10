package com.nhomX.example.exception;
// Lỗi khi giá không hợp lệ:
public class InvalidBidException extends RuntimeException{
    public InvalidBidException(String message) {
        super(message);
    }
}

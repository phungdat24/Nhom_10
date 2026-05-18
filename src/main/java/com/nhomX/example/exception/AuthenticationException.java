package com.nhomX.example.exception;
// Lỗi chưa xác thực
public class AuthenticationException extends RuntimeException{
    public AuthenticationException(String message){
        super(message);
    }
}

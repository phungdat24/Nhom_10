package com.nhomX.example.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SecurityUtils {
    public static String hashPassword(String password){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[]  hash = md.digest(password.getBytes());
            StringBuilder string = new StringBuilder();
            for ( byte b : hash){
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    string.append('0');
                }
                string.append(hex);
            }
            return string.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán băm mật khẩu", e);
        }
    }
}


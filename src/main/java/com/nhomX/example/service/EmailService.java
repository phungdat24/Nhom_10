package com.nhomX.example.service;

import java.util.concurrent.CompletableFuture;

public interface EmailService {
    /**
     * Định nghĩa hợp đồng: Bất kỳ dịch vụ mail nào cũng phải có hàm gửi OTP
     * @param recipientEmail Email của người nhận (tài khoản đăng ký)
     * @param otpCode Mã số OTP gồm 6 chữ số
     */
    CompletableFuture<Boolean> sendOtp(String recipientEmail, String otpCode);
}

package com.nhomX.example.service;

public interface EmailService {
    /**
     * Định nghĩa hợp đồng: Bất kỳ dịch vụ mail nào cũng phải có hàm gửi OTP
     * @param recipientEmail Email của người nhận (tài khoản đăng ký)
     * @param otpCode Mã số OTP gồm 6 chữ số
     */
    void sendOtp(String recipientEmail, String otpCode);
}

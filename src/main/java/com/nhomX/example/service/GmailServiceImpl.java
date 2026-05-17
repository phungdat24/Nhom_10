package com.nhomX.example.service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class GmailServiceImpl implements EmailService{
    // Điền Email của em và cái Mật khẩu 16 chữ cái vừa tạo ở Bước 2 vào đây
    private static final String MY_EMAIL = "phungtiendat.it@gmail.com";
    private static final String APP_PASSWORD = "eouk bhen qngk dpjw";

    /**
     * Hàm gửi OTP chạy ngầm (Bất đồng bộ)
     */
    @Override
    public CompletableFuture<Boolean> sendOtp(String recipientEmail, String otpCode) {
        // Chạy trong luồng riêng để không làm đơ giao diện
        return CompletableFuture.supplyAsync(() -> {
            // 1. Cấu hình thông số máy chủ SMTP của Google
            Properties prop = new Properties();
            prop.put("mail.smtp.host", "smtp.gmail.com");
            prop.put("mail.smtp.port", "587");
            prop.put("mail.smtp.auth", "true");
            prop.put("mail.smtp.starttls.enable", "true"); // Bảo mật TLS

            // 2. Đăng nhập vào Gmail của em
            Session session = Session.getInstance(prop, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(MY_EMAIL, APP_PASSWORD);
                }
            });

            try {
                // 3. Soạn nội dung Bức thư
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(MY_EMAIL, "Hệ Thống Đấu Giá Nhóm X", "UTF-8"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject("Mã xác thực đăng ký tài khoản (OTP)");

                // Trình bày nội dung đẹp mắt bằng HTML
                String htmlContent = "<h2 style='color: #c9a227;'>Xin chào,</h2>"
                        + "<p>Mã xác thực (OTP) của bạn là: <b style='font-size: 24px; color: red;'>" + otpCode + "</b></p>"
                        + "<p>Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>"
                        + "<p>Trân trọng,<br>Ban quản trị Nhom X</p>";

                message.setContent(htmlContent, "text/html; charset=utf-8");

                // 4. Bấm nút "Gửi"
                Transport.send(message);
                System.out.println("✅ Đã gửi OTP " + otpCode + " tới email: " + recipientEmail);
                return true;
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi gửi mail: " + e.getMessage());
                return false;
            }
        });
    }
}

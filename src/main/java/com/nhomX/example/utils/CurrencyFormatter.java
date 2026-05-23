package com.nhomX.example.utils;

import java.text.DecimalFormat;

public class CurrencyFormatter {
    // Định dạng xèng
    public static String formatVND(long amount){
        DecimalFormat formatter = new DecimalFormat("#,###");
        String formatterNumber = formatter.format(amount).replace(",",".");
        return formatterNumber + " VNĐ";
    }
    // Định dạng so:
    public static String formatNumber(long amount){
        DecimalFormat formatter = new DecimalFormat("#,###");
        return formatter.format(amount).replace(",",".");
    }
    // THUẬT TOÁN ĐỌC SỐ THÀNH CHỮ
    private static final String[] UNITS = {"", "Một", "Hai", "Ba", "Bốn", "Năm", "Sáu", "Bảy", "Tám", "Chín"};
    private static final String[] SCALES = {"", "Nghìn", "Triệu", "Tỷ", "Nghìn tỷ", "Triệu tỷ","Tỷ tỷ"};

    public static String numberToWords(long number) {
        if (number == 0) return "Không";
        if (number < 0) return "Âm " + numberToWords(Math.abs(number));

        String words = "";
        int scaleIndex = 0;

        while (number > 0) {
            int threeDigits = (int) (number % 1000);
            if (threeDigits > 0) {
                String chunk = readThreeDigits(threeDigits);
                words = chunk + " " + SCALES[scaleIndex] + " " + words;
            }
            number /= 1000;
            scaleIndex++;
        }

        // Viết hoa chữ cái đầu tiên và xóa khoảng trắng thừa
        words = words.trim().replaceAll(" +", " ");
        return words.substring(0, 1).toUpperCase() + words.substring(1);
    }

    private static String readThreeDigits(int n) {
        int hundred = n / 100;
        int ten = (n % 100) / 10;
        int unit = n % 10;

        String result = "";
        if (hundred > 0) result += UNITS[hundred] + " Trăm ";

        if (ten == 0 && unit > 0 && hundred > 0) result += "Lẻ ";
        if (ten == 1) result += "Mười ";
        if (ten > 1) result += UNITS[ten] + " Mươi ";

        if (unit > 0) {
            if (ten > 0 && unit == 5) result += "Lăm";
            else if (ten > 1 && unit == 1) result += "Mốt";
            else result += UNITS[unit];
        }
        return result.trim();
    }
}

package com.nhomX.example.utils;

import java.text.DecimalFormat;

public class CurrencyFormatter {
    // Định dạng xèng
    public static String formatVND(double amount){
        DecimalFormat formatter = new DecimalFormat("#,###");
        String formatterNumber = formatter.format(amount).replace(",",".");
        return formatterNumber + " VNĐ";
    }
    // Định dạng so:
    public static String formatNumber(double amount){
        DecimalFormat formatter = new DecimalFormat("#,###");
        return formatter.format(amount).replace(",",".");
    }
}

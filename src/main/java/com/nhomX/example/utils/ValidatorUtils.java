package com.nhomX.example.utils;

public class ValidatorUtils {
    public static boolean isValidPrice(String priceStr){
        if(priceStr == null || priceStr.trim().isEmpty()){
            return false;
        }
        try {
            long price = Long.parseLong(priceStr);
            return price >0;
        }catch (NumberFormatException e){
            // Nhập chữ sẽ lỗi
            return false;
        }
    }
    public static boolean isValidEmail(String email){
        if(email == null ){
            return false;
        }
        String emailRegex="^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
        return email.matches(emailRegex);
    }
}

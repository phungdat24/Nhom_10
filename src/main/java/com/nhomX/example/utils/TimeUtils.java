package com.nhomX.example.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static String formatDateTime(LocalDateTime dateTime){
        if(dateTime == null){
            return "";
        }
        return dateTime.format(formatter);
    }
    public static String getCountdownString(LocalDateTime endTime){
        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(endTime)){
            return "Đã kết thúc";
        }
        Duration duration = Duration.between(now, endTime);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes,seconds );
    }
}

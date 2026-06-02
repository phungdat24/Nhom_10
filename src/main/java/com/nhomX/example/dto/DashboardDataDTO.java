package com.nhomX.example.dto;

import com.nhomX.example.model.Auction;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DTO đóng gói toàn bộ dữ liệu Dashboard Admin.
 * Serializable để truyền qua ObjectOutputStream (Java Socket).
 */
public class DashboardDataDTO implements Serializable {

    // ── KPI Cards ─────────────────────────────────────────────────────────
    /** Tổng user trong DB (bao gồm cả inactive). */
    public int totalUsers;
    /** Số phiên đang OPEN hoặc RUNNING. */
    public int liveAuctions;
    /** Tổng highest_bid của tất cả phiên FINISHED/PAID — đại diện dòng tiền lưu thông. */
    public long totalRevenue;
    /** Số user đang kết nối socket trực tiếp. */
    public int onlineUsers;

    // ── AreaChart: doanh thu 7 ngày ───────────────────────────────────────
    /**
     * Key: "dd/MM" (VD: "25/05"), Value: tổng highest_bid FINISHED trong ngày đó.
     * LinkedHashMap để giữ thứ tự tăng dần theo ngày.
     */
    public Map<String, Long> revenueByDay;

    // ── Category breakdown ────────────────────────────────────────────────
    /** Key: tên danh mục (VD: "JEWELRY"), Value: số phiên FINISHED. */
    public Map<String, Integer> finishedByCategory;

    // ── PieChart ──────────────────────────────────────────────────────────
    public int countLive;       // OPEN + RUNNING
    public int countPending;    // PENDING + UP_COMING
    public int countClosed;     // FINISHED + CANCELED + PAID

    // ── Recent Transactions ───────────────────────────────────────────────
    /** 5 phiên FINISHED/PAID gần nhất, đã JOIN đủ thông tin winner và item. */
    public List<Auction> recentTransactions;
}

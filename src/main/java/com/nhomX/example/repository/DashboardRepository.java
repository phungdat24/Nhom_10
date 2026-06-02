package com.nhomX.example.repository;

import com.nhomX.example.dto.DashboardDataDTO;
import com.nhomX.example.model.Auction;
import com.nhomX.example.model.AuctionStatus;
import com.nhomX.example.model.GeneralItem;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardRepository {

    private static final DateTimeFormatter DB_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LABEL_FMT =
            DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Truy vấn một lần duy nhất, trả về toàn bộ DTO.
     * Mỗi SQL độc lập — dễ tối ưu index sau này.
     */
    public DashboardDataDTO buildDashboardData(int onlineUsers) {
        DashboardDataDTO dto = new DashboardDataDTO();
        dto.onlineUsers = onlineUsers;

        Connection conn = DatabaseConnection.getInstance().getConnection();

        // 1. KPI — Tổng user
        dto.totalUsers = queryInt(conn,
                "SELECT COUNT(*) FROM users");

        // 2. KPI — Phiên đang live
        dto.liveAuctions = queryInt(conn,
                "SELECT COUNT(*) FROM auctions WHERE status IN ('OPEN','RUNNING')");

        // 3. KPI — Tổng dòng tiền (SUM highest_bid của các phiên FINISHED/PAID)
        dto.totalRevenue = queryLong(conn,
                "SELECT COALESCE(SUM(highest_bid), 0) FROM auctions "
                        + "WHERE status IN ('FINISHED','PAID')");

        // 4. AreaChart — Doanh thu 7 ngày gần nhất
        dto.revenueByDay = queryRevenueByDay(conn, 7);

        // 5. Category breakdown — JOIN auctions + items, đếm FINISHED theo category
        dto.finishedByCategory = queryCategoryBreakdown(conn);

        // 6. PieChart counts
        dto.countLive = queryInt(conn,
                "SELECT COUNT(*) FROM auctions WHERE status IN ('OPEN','RUNNING')");
        dto.countPending = queryInt(conn,
                "SELECT COUNT(*) FROM auctions WHERE status IN ('PENDING','UP_COMING')");
        dto.countClosed = queryInt(conn,
                "SELECT COUNT(*) FROM auctions WHERE status IN ('FINISHED','CANCELED','PAID')");

        // 7. Recent Transactions — 5 phiên FINISHED gần nhất
        dto.recentTransactions = queryRecentTransactions(conn, 5);

        return dto;
    }

    // ── SQL: Doanh thu theo ngày ─────────────────────────────────────────
    private Map<String, Long> queryRevenueByDay(Connection conn, int days) {
        // LinkedHashMap giữ thứ tự ngày tăng dần
        Map<String, Long> result = new LinkedHashMap<>();

        // Khởi tạo tất cả ngày = 0 trước (tránh ngày trống không hiện trên chart)
        for (int i = days - 1; i >= 0; i--) {
            String label = LocalDate.now().minusDays(i).format(LABEL_FMT);
            result.put(label, 0L);
        }

        String sql =
                "SELECT DATE(end_time) AS day, COALESCE(SUM(highest_bid), 0) AS total "
                        + "FROM auctions "
                        + "WHERE status IN ('FINISHED','PAID') "
                        + "  AND end_time >= DATE('now', '-" + (days - 1) + " days') "
                        // ↑ SQLite: thay bằng DATE_SUB(NOW(), INTERVAL ? DAY) nếu dùng MySQL
                        + "GROUP BY DATE(end_time) "
                        + "ORDER BY day ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // Parse "yyyy-MM-dd" → label "dd/MM"
                LocalDate date = LocalDate.parse(rs.getString("day"), DB_DATE_FMT);
                String label = date.format(LABEL_FMT);
                result.put(label, rs.getLong("total"));
            }
        } catch (SQLException e) {
            System.err.println("❌ queryRevenueByDay: " + e.getMessage());
        }
        return result;
    }

    // ── SQL: Đếm FINISHED theo danh mục sản phẩm ────────────────────────
    private Map<String, Integer> queryCategoryBreakdown(Connection conn) {
        Map<String, Integer> result = new LinkedHashMap<>();

        String sql =
                "SELECT COALESCE(UPPER(i.category), 'GENERALITEM') AS category, "
                        + "COUNT(a.id) AS total "
                        + "FROM auctions a "
                        + "JOIN items i ON a.item_id = i.id "
                        + "WHERE a.status IN ('FINISHED','PAID') "
                        + "GROUP BY COALESCE(UPPER(i.category), 'GENERALITEM') "
                        + "ORDER BY total DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("category"), rs.getInt("total"));
            }
        } catch (SQLException e) {
            System.err.println("❌ queryCategoryBreakdown: " + e.getMessage());
        }
        return result;
    }

    // ── SQL: 5 giao dịch FINISHED gần nhất ──────────────────────────────
    private List<Auction> queryRecentTransactions(Connection conn, int limit) {
        List<Auction> list = new ArrayList<>();

        // JOIN 3 bảng: auctions + items (tên SP) + users (tên người thắng)
        String sql =
                "SELECT a.id, a.highest_bid, a.end_time, a.status, "
                        + "       i.title AS item_title, "
                        + "       u.fullname AS winner_name "
                        + "FROM auctions a "
                        + "JOIN items i ON a.item_id = i.id "
                        + "LEFT JOIN users u ON a.winner_id = u.id "
                        + "WHERE a.status IN ('FINISHED','PAID') "
                        + "ORDER BY a.end_time DESC "
                        + "LIMIT ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Auction auction = new Auction();
                    auction.setId(rs.getString("id"));
                    auction.setHighestBid(rs.getLong("highest_bid"));

                    // Nhét tên SP vào "vỏ rỗng" GeneralItem
                    GeneralItem item = new GeneralItem();
                    item.setTitle(rs.getString("item_title"));
                    auction.setItem(item);

                    // Nhét tên người thắng vào "vỏ rỗng" User
                    String winnerName = rs.getString("winner_name");
                    if (winnerName != null) {
                        RegularUser winner = new RegularUser();
                        winner.setFullName(winnerName);
                        auction.setWinner(winner);
                    }

                    String status = rs.getString("status");
                    if (status != null) {
                        try {
                            auction.setStatus(AuctionStatus.valueOf(status));
                        } catch (IllegalArgumentException ignored) {
                            // Giữ trạng thái mặc định nếu DB chứa giá trị không hợp lệ.
                        }
                    }

                    list.add(auction);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ queryRecentTransactions: " + e.getMessage());
        }
        return list;
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private int queryInt(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("❌ queryInt [" + sql + "]: " + e.getMessage());
            return 0;
        }
    }

    private long queryLong(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            System.err.println("❌ queryLong [" + sql + "]: " + e.getMessage());
            return 0L;
        }
    }
}

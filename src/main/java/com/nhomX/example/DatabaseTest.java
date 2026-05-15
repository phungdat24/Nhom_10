package com.nhomX.example;

import java.sql.Connection;
import java.sql.Statement;
import com.nhomX.example.utils.DatabaseConnection;
import com.nhomX.example.utils.SecurityUtils;

public class DatabaseTest {
        public static void main(String[] args) {
                try (Connection conn = DatabaseConnection.getInstance().getConnection();
                                Statement stmt = conn.createStatement()) {

                        // 1. Xóa dữ liệu cũ (Phải tách riêng từng lệnh để SQLite chạy thành công)
                        stmt.execute("DELETE FROM auto_bids");
                        stmt.execute("DELETE FROM bids");
                        stmt.execute("DELETE FROM auctions");
                        stmt.execute("DELETE FROM item_images");
                        stmt.execute("DELETE FROM items");
                        stmt.execute("DELETE FROM users");

                        // 2. Nạp dữ liệu mới (Cũng tách riêng từng bảng)

                        // --- USERS ---
                        String pass = SecurityUtils.hashPassword("123456789");
                        String insertUsers =
                                        "INSERT INTO users (id, username, password, fullname, balance, role) VALUES "
                                                        + "('U001', 'seller1','"+pass+"'  , 'Nguyen Van Ban', 5000000000000000, 'USER'), "
                                                        + "('U002', 'buyer1', '123', 'Tran Thi Mua', 500000000, 'USER')";
                        stmt.executeUpdate(insertUsers);

                        // --- ITEMS (30 món hoành tráng) ---
                        String insertItems =
                                        "INSERT INTO items (id, title, description, category, seller_id) VALUES "
                                                        + "('ITM001', 'Laptop Dell XPS 15', 'Máy cũ 99%, chip i7', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM002', 'Đồng hồ Rolex', 'Hàng chính hãng, full box', 'JEWELRY', 'U001'), "
                                                        + "('ITM003', 'Tranh Đêm Đầy Sao', 'Bản sao sơn dầu', 'ART', 'U001'), "
                                                        + "('ITM004', 'iPhone 15 Pro Max', 'Màu Titan tự nhiên', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM005', 'Dây chuyền vàng', 'Đính đá Sapphire', 'JEWELRY', 'U001'), "
                                                        + "('ITM006', 'Tượng Quan Âm', 'Chạm khắc thủ công', 'ART', 'U001'), "
                                                        + "('ITM007', 'Tai nghe Sony', 'Chống ồn đỉnh cao', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM008', 'Nhẫn kim cương', 'Giấy kiểm định GIA', 'JEWELRY', 'U001'), "
                                                        + "('ITM009', 'MacBook Pro M3', 'Dành cho Creator', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM010', 'Bình sứ Minh Long', 'Bản giới hạn', 'ART', 'U001'), "
                                                        + "('ITM011', 'iPad Pro M4', 'Mỏng nhẹ, màn OLED', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM012', 'Máy chơi game PS5', 'Bản ổ đĩa kèm 2 tay cầm', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM013', 'Kính Apple Vision Pro', 'Trải nghiệm không gian ảo', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM014', 'Tranh Thêu Chữ Thập', 'Hình Mã Đáo Thành Công', 'ART', 'U001'), "
                                                        + "('ITM015', 'Khuyên tai Đính Ngọc Trai', 'Ngọc trai Phú Quốc', 'JEWELRY', 'U001'), "
                                                        + "('ITM016', 'Vòng tay Trầm Hương', 'Mùi hương tự nhiên', 'JEWELRY', 'U001'), "
                                                        + "('ITM017', 'Đèn Chùm Pha Lê', 'Trang trí phòng khách', 'ART', 'U001'), "
                                                        + "('ITM018', 'Loa Marshall Stanmore 3', 'Âm thanh cổ điển', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM019', 'Bàn phím cơ Logitech', 'Switch Linear gõ siêu êm', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM020', 'Chuột Razer DeathAdder', 'Công thái học', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM021', 'Màn hình LG 27 inch 4K', 'Chuẩn màu đồ họa', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM022', 'Vòng cổ ngọc bích', 'Ngọc tự nhiên', 'JEWELRY', 'U001'), "
                                                        + "('ITM023', 'Lắc tay vàng trắng', 'Kiểu dáng thanh lịch', 'JEWELRY', 'U001'), "
                                                        + "('ITM024', 'Tranh lụa hoa sen', 'Nghệ thuật truyền thống', 'ART', 'U001'), "
                                                        + "('ITM025', 'Tượng đồng phong thủy', 'Linh vật tỳ hưu', 'ART', 'U001'), "
                                                        + "('ITM026', 'Loa Harman Kardon', 'Thiết kế trong suốt', 'ELECTRONICS', 'U001'), "
                                                        + "('ITM027', 'Bút máy Parker', 'Ngòi mạ vàng', 'GENERALITEM', 'U001'), "
                                                        + "('ITM028', 'Ghế Herman Miller', 'Hỗ trợ cột sống', 'GENERALITEM', 'U001'), "
                                                        + "('ITM029', 'Đồng hồ quả lắc', 'Sản xuất thế kỷ 19', 'ART', 'U001'), "
                                                        + "('ITM030', 'Dây da Apple Watch', 'Da thủ công Pháp', 'JEWELRY', 'U001')";
                        stmt.executeUpdate(insertItems);

                        // --- IMAGES ---
                        String insertImages =
                                        "INSERT INTO item_images (id, image_path, item_id) VALUES "
                                                        + "('IMG001', 'item1.png', 'ITM001'), ('IMG002', 'item2.png', 'ITM002'), "
                                                        + "('IMG003', 'item3.png', 'ITM003'), ('IMG004', 'item4.png', 'ITM004'), "
                                                        + "('IMG005', 'item5.png', 'ITM005'), ('IMG006', 'item6.png', 'ITM006'), "
                                                        + "('IMG007', 'item7.png', 'ITM007'), ('IMG008', 'item8.png', 'ITM008'), "
                                                        + "('IMG009', 'item9.png', 'ITM009'), ('IMG010', 'item10.png', 'ITM010'), "
                                                        + "('IMG011', 'ipad.png', 'ITM011'), ('IMG012', 'ps5.png', 'ITM012'), "
                                                        + "('IMG013', 'vision.png', 'ITM013'), ('IMG014', 'tranh_theu.png', 'ITM014'), "
                                                        + "('IMG015', 'khuyen_tai.png', 'ITM015'), ('IMG016', 'tram_huong.png', 'ITM016'), "
                                                        + "('IMG017', 'den_chum.png', 'ITM017'), ('IMG018', 'marshall.png', 'ITM018'), "
                                                        + "('IMG019', 'logitech.png', 'ITM019'), ('IMG020', 'razer.png', 'ITM020'), "
                                                        + "('IMG021', 'lg27.png', 'ITM021'), ('IMG022', 'ngoc_bich.png', 'ITM022'), "
                                                        + "('IMG023', 'lac_tay.png', 'ITM023'), ('IMG024', 'tranh_lua.png', 'ITM024'), "
                                                        + "('IMG025', 'ty_huu.png', 'ITM025'), ('IMG026', 'harman.png', 'ITM026'), "
                                                        + "('IMG027', 'parker.png', 'ITM027'), ('IMG028', 'herman.png', 'ITM028'), "
                                                        + "('IMG029', 'dong_ho_co.png', 'ITM029'), ('IMG030', 'hermes.png', 'ITM030')";
                        stmt.executeUpdate(insertImages);

                        // --- AUCTIONS (Kết thúc năm 2027) ---
                        String insertAuctions =
                                        "INSERT INTO auctions (id, starting_price, highest_bid, start_time, end_time, status, item_id) VALUES "
                                                        + "('AUC001', 15000, 150000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM001'), "
                                                        + "('AUC002', 80000, 800000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM002'), "
                                                        + "('AUC003', 25000, 250000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM003'), "
                                                        + "('AUC004', 30000, 300000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM004'), "
                                                        + "('AUC005', 12000, 120000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM005'), "
                                                        + "('AUC006', 80000, 800000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM006'), "
                                                        + "('AUC007', 50000, 500000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM007'), "
                                                        + "('AUC008', 35000, 350000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM008'), "
                                                        + "('AUC009', 45000, 450000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM009'), "
                                                        + "('AUC010', 60000, 600000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM010'), "
                                                        + "('AUC011', 28000, 280000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM011'), "
                                                        + "('AUC012', 14000, 140000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM012'), "
                                                        + "('AUC013', 85000, 850000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM013'), "
                                                        + "('AUC014', 50000, 500000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM014'), "
                                                        + "('AUC015', 30000, 300000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM015'), "
                                                        + "('AUC016', 12000, 120000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM016'), "
                                                        + "('AUC017', 22000, 220000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM017'), "
                                                        + "('AUC018', 75000, 750000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM018'), "
                                                        + "('AUC019', 20000, 200000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM019'), "
                                                        + "('AUC020', 15000, 150000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM020'), "
                                                        + "('AUC021', 90000, 900000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM021'), "
                                                        + "('AUC022', 18000, 180000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM022'), "
                                                        + "('AUC023', 11000, 110000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM023'), "
                                                        + "('AUC024', 45000, 450000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM024'), "
                                                        + "('AUC025', 95000, 950000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM025'), "
                                                        + "('AUC026', 65000, 650000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM026'), "
                                                        + "('AUC027', 18000, 180000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM027'), "
                                                        + "('AUC028', 32000, 320000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM028'), "
                                                        + "('AUC029', 55000, 550000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM029'), "
                                                        + "('AUC030', 89000, 890000, '2026-05-10 08:00:00', '2027-12-31 23:59:00', 'OPEN', 'ITM030')";
                        stmt.executeUpdate(insertAuctions);

                        System.out.println("--------------------------------------------------");
                        System.out.println(
                                        "✅ THÀNH CÔNG: Đã nạp dữ liệu mẫu 30 sản phẩm vào cấu trúc mới!");
                        System.out.println("   - Đã xóa sạch dữ liệu cũ.");
                        System.out.println("   - Đã tạo 2 người dùng mẫu.");
                        System.out.println("   - Đã tạo 30 sản phẩm.");
                        System.out.println("   - Đã tách 30 ảnh vào bảng item_images.");
                        System.out.println("   - Đã đưa 30 sản phẩm lên sàn đấu giá (auctions).");
                        System.out.println("--------------------------------------------------");

                } catch (Exception e) {
                        System.err.println("❌ Lỗi khi nạp dữ liệu test: " + e.getMessage());
                        e.printStackTrace();
                }
        }
}

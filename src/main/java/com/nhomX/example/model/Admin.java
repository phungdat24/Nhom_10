package com.nhomX.example.model;

public class Admin extends User{
    // 1. Hàm tạo rỗng (Bắt buộc cho việc đọc từ DB/Truyền qua mạng)
    public Admin() {
        super();
    }

    // 2. Hàm tạo đầy đủ tham số
    public Admin(String id, String userName, String passwordHash, String fullName, long balance) {
        super(id, userName, passwordHash, fullName, balance);
    }
    // TRIỂN KHAI HÀM CỦA LỚP CHA (OVERRIDE)

    /**
     * Với Admin, quyền hạn là tuyệt đối và cố định,
     * không cần phải dùng Set<Role> để thêm bớt phức tạp như RegularUser.
     */
    @Override
    public String getRoleName() {
        return Role.ADMIN.name(); // Luôn luôn trả về chuỗi "ADMIN"
    }

    // CÁC HÀNH VI NGHIỆP VỤ ĐẶC QUYỀN (ADMIN BEHAVIORS)
    /**
     * Quyền lực 1: Hủy bỏ một phiên đấu giá khẩn cấp.
     * (Ví dụ: Khi phát hiện người bán đăng sản phẩm giả mạo, vi phạm pháp luật).
     * * @param auction Phiên đấu giá cần hủy
     * @param reason Lý do hủy để ghi log
     */
    public void forceCancelAuction(Auction auction, String reason) {
        if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.PAID) {
            throw new IllegalStateException("Không thể hủy phiên đấu giá đã kết thúc và chốt người thắng!");
        }

        auction.setStatus(AuctionStatus.CANCELED);
        // Trong thực tế, hệ thống sẽ lưu lại 'reason' vào bảng log hoặc gửi thông báo cho các bidder.
        System.out.println("⚠️ ADMIN " + this.getUserName() + " ĐÃ HỦY phiên đấu giá " + auction.getId());
        System.out.println("Lý do: " + reason);
    }

    /**
     * Quyền lực 2: Khóa tài khoản người dùng vi phạm.
     * (Ví dụ: Bidder boom hàng, Seller lừa đảo).
     * * @param user Tài khoản RegularUser bị khóa
     */
    public void banUser(RegularUser user) {
        // Xóa toàn bộ quyền Mua và Bán của user này
        user.removeRole(Role.BIDDER);
        user.removeRole(Role.SELLER);

        System.out.println("🚫 ADMIN " + this.getUserName() + " ĐÃ KHÓA tài khoản " + user.getUserName());
    }

    /**
     * Quyền lực 3: Nạp tiền trực tiếp cho người dùng.
     * (Thường dùng trong các trường hợp hỗ trợ hoàn tiền hoặc xử lý sự cố).
     */

    public void addFundsToUser(RegularUser user, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
        user.updateBalance(amount);
        System.out.println("💰 ADMIN " + this.getUserName() + " đã nạp " + amount + " vào tài khoản " + user.getUserName());
    }
    /**
     * [THÊM MỚI] Duyệt phiên đấu giá từ trạng thái PENDING → OPEN.
     * Theo ERD, auctions có trường approved_by tham chiếu đến users.
     *
     * @param auction Phiên cần duyệt.
     */
    public void approveAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt phiên ở trạng thái PENDING!");
        }
        auction.setStatus(AuctionStatus.OPEN);
        System.out.println("✅ ADMIN " + this.getUserName()
                + " đã duyệt phiên đấu giá " + auction.getId());
    }
}

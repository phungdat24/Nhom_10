package com.nhomX.example.model;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RegularUser extends User {
    // Thuộc tính cực kỳ quan trọng: Tập hợp các quyền của người dùng này
    // Dùng Set thay vì List để đảm bảo 1 người không bị add 2 quyền SELLER trùng nhau
    private Set<Role> roles;

    //  Hàm tạo rỗng (Rất quan trọng để không bị lỗi NullPointerException)
    public RegularUser() {
        // Gọi hàm tạo của lớp cha User
        super();
        // BẮT BUỘC phải khởi tạo danh sách rỗng
        this.roles = new HashSet<>();
    }

    // 2. Hàm tạo có tham số (Dùng khi lấy từ Database lên)
    public RegularUser(String id, String userName, String passwordHash, String fullName, long balance) {
        super(id, userName, passwordHash, fullName, balance);
        this.roles = new HashSet<>();
    }
    // PHẦN 1: QUẢN LÝ QUYỀN HẠN (AUTHORIZATION)

    /**
     * Thêm một quyền cho User (Ví dụ: Cấp quyền bán hàng)
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Xóa một quyền của User (Ví dụ: Tước quyền bán hàng nếu vi phạm)
     */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * HÀM QUAN TRỌNG NHẤT: Kiểm tra User có quyền thực hiện hành động không
     * Trả về true nếu có quyền, false nếu không.
     */
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    // Ghi đè hàm của lớp cha để hiển thị lên giao diện (Ví dụ: Trả về "BIDDER, SELLER")
    @Override
    public String getRoleName() {
        if (roles.isEmpty() || roles == null) {
            // Chưa có quyền gì
            return "GUEST";
        }
        // Dùng Java 8 Stream để nối các quyền lại thành 1 chuỗi String có dấu phẩy
        return roles.stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public Set<Role> getRoles() {
        return roles;
    }
    // PHẦN 2: CÁC HÀNH VI NGHIỆP VỤ (OOP BEHAVIORS)

    /**
     * Hàm đặt giá (Chỉ thực hiện được nếu có quyền BIDDER)
     */
    public BidTransaction placeBid(Auction auction, long amount) {
        // Kiểm tra quyền (Bảo mật 2 lớp)
        if (!this.hasRole(Role.BIDDER)) {
            throw new IllegalStateException("Tài khoản của bạn chưa được cấp quyền tham gia đấu giá!");
        }
        if (!auction.canAcceptBids()) {
            throw new IllegalStateException("Phiên đấu giá này đã đóng hoặc chưa bắt đầu!");
        }
        if (amount <= auction.getHighestBid()) {
            throw new IllegalArgumentException(
                    "Giá đặt phải cao hơn giá hiện tại: " + auction.getHighestBid());
        }
        // Khởi tạo một giao dịch Bid mới
        BidTransaction newBid = new BidTransaction();
        newBid.generateId(); // Hàm từ Entity
        newBid.setAmount(amount);
        newBid.setBidder(this);   // Tham chiếu Object: Người đặt là TÔI
        newBid.setAuction(auction); // Tham chiếu Object: Đặt vào phiên này
        newBid.setBidTime(java.time.LocalDateTime.now());

        return newBid;
    }
    /**
     * Thiết lập cấu hình đấu giá tự động (Auto Bid).
     * Chỉ thực hiện được nếu user có quyền BIDDER và phiên đấu giá đang mở.
     * * @param auction Phiên đấu giá muốn thiết lập Auto-bid
     * @param maxLimit Giới hạn giá cao nhất sẵn sàng trả
     * @param increment Bước giá tự động cộng thêm mỗi lần
     * @return Đối tượng cấu hình AutoBidConfig đã được tạo
     */
    public AutoBidConfig setupAutoBid(Auction auction, long maxLimit, long increment) {
        // 1. Kiểm tra quyền của người dùng (Giống hệt lúc placeBid)
        if (!this.hasRole(Role.BIDDER)) {
            throw new IllegalStateException("Tài khoản của bạn chưa được cấp quyền tham gia đấu giá!");
        }

        // 2. Kiểm tra xem phiên đấu giá còn nhận đặt giá không
        if (!auction.canAcceptBids()) {
            throw new IllegalStateException("Phiên đấu giá này đã đóng hoặc chưa bắt đầu!");
        }
        if (maxLimit <= auction.getHighestBid()) {
            throw new IllegalArgumentException(
                    "Giới hạn giá tối đa phải cao hơn giá hiện tại: " + auction.getHighestBid());
        }

        // 3. Khởi tạo cấu hình và liên kết các đối tượng
        AutoBidConfig config = new AutoBidConfig();
        config.generateId(); // Sinh ID ngẫu nhiên từ Entity
        config.setAuction(auction); // Tham chiếu đến phiên đấu giá
        config.setBidder(this);     // Tham chiếu đến chính người dùng này (TÔI)
        config.setMaxLimit(maxLimit);
        config.setIncrement(increment);

        System.out.println("Đã cài đặt Auto-Bid cho " + this.getUserName() + " với ngưỡng tối đa: " + maxLimit);

        return config;
    }

    /**
     * Hàm đăng bán sản phẩm (Chỉ thực hiện được nếu có quyền SELLER)
     */
    public void addProduct(Items item) {
        if (!this.hasRole(Role.SELLER)) {
            throw new IllegalStateException("Tài khoản của bạn chưa được cấp quyền đăng bán sản phẩm!");
        }
        // Gán tôi làm chủ nhân của món đồ này
        item.setSeller(this);
    }

    /**
     * Hành động của người dùng (Seller) để chủ động kết thúc phiên đấu giá của mình.
     *
     * @param auction Phiên đấu giá muốn đóng
     */
    public void closeAuction(Auction auction) {
        // 1. Kiểm tra quyền sở hữu (Chỉ người bán món đồ này mới được đóng phiên)
        if (this.getId() != null && this.getId().equals(auction.getItem().getSeller().getId())) {

            // 2. Nếu đúng là chủ, mới gọi hàm logic nội bộ của Auction để thực hiện chốt
            auction.closeAuction();

            System.out.println("Người bán " + this.getUserName() + " đã chủ động kết thúc phiên: " + auction.getId());
        } else {
            // 3. Nếu kẻ lạ định đóng phiên, ném ra lỗi bảo mật
            throw new IllegalStateException("Lỗi bảo mật: Bạn không có quyền kết thúc phiên đấu giá không phải của mình!");
        }
    }
}

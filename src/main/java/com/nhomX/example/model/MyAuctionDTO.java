package com.nhomX.example.model;

import java.io.Serializable;
/**
 * DTO (Data Transfer Object) dành riêng cho màn hình "Sản phẩm của tôi" (My Auctions).
 * Object này không được lưu xuống Database, nó chỉ dùng làm "giỏ đựng" để gom
 * thông tin của phiên đấu giá và thông tin cá nhân của User gửi qua mạng Internet.
 */

public class MyAuctionDTO implements Serializable {
    // Đảm bảo tính đồng bộ dữ liệu khi truyền Object qua Socket (Tránh lỗi InvalidClassException)
    private static final long serialVersionUID = 1L;

    // 1. Thông tin chung của phiên đấu giá (Tên, ảnh, giá hiện hành của Server...)
    private Auction auction;

    // 2. Mức giá cao nhất mà RIÊNG BẠN (User đang đăng nhập) đã từng đặt cho món này
    private long myHighestBid;

    // 3. Trạng thái hiện tại của BẠN trong phiên đấu giá này
    private MyAuctionStatus myStatus;

    /**
     * Constructor rỗng (No-args constructor)
     * Rất quan trọng khi dùng các thư viện tự động map dữ liệu (như Jackson, Hibernate).
     */
    public MyAuctionDTO() {
    }

    /**
     * Constructor đầy đủ tham số (All-args constructor)
     */
    public MyAuctionDTO(Auction auction, long myHighestBid, MyAuctionStatus myStatus) {
        this.auction = auction;
        this.myHighestBid = myHighestBid;
        this.myStatus = myStatus;
    }

    // ==========================================
    //            GETTERS & SETTERS
    // ==========================================

    public Auction getAuction() {
        return auction;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public long getMyHighestBid() {
        return myHighestBid;
    }

    public void setMyHighestBid(long myHighestBid) {
        this.myHighestBid = myHighestBid;
    }

    public MyAuctionStatus getMyStatus() {
        return myStatus;
    }
    /**
     * Hàm ủy quyền lấy Trạng thái (Enum) từ thực thể Auction bên trong
     */
    public AuctionStatus getStatus() { // Đổi AuctionStatus thành tên Enum chính xác của em nếu khác
        return this.auction.getStatus();
    }
    /**
     * Hàm ủy quyền lấy Giá cao nhất từ thực thể Auction bên trong
     */
    public long getHighestBid() {
        // Giả sử DTO của em đang lưu đối tượng Auction dưới tên biến là 'auction'
        return this.auction.getHighestBid();
    }

    public void setMyStatus(MyAuctionStatus myStatus) {
        this.myStatus = myStatus;
    }
    //      HÀM HỖ TRỢ DEBUG

    @Override
    public String toString() {
        return "MyAuctionDTO{" +
                "auctionName=" + (auction != null && auction.getItem() != null ? auction.getItem().getTitle() : "null") +
                ", myHighestBid=" + myHighestBid +
                ", myStatus='" + myStatus + '\'' +
                '}';
    }
}

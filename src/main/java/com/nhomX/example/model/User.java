package com.nhomX.example.model;

public abstract class User extends Entity{
    // Email:
    private String userName;
    // Mật khẩu:
    private String passwordHash;
    //Họ và tên:
    private String fullName;
    // Số dư tài khoản:
    private long balance;
    private boolean isActive = true;
    // Hàm khởi tạo rỗng:
    public User(){
    }
    public User(String id, String userName, String passwordHash, String fullName, long balance, boolean isActive){
        super(id);
        this.userName = userName;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.setBalance(balance);
        this.isActive= isActive;
    }
    // CÁC HÀM NGHIỆP VỤ (BUSINESS LOGIC)
    // ==========================================

    /**
     * Cập nhật số dư dựa trên biến động (Nạp tiền hoặc Trừ tiền).
     * @param amount Số tiền biến động (Dương để nạp, Âm để trừ).
     * @throws IllegalArgumentException Nếu số dư không đủ để thực hiện giao dịch trừ tiền.
     */
    public void updateBalance(long amount) {
        if (amount < 0 && Math.abs(amount) > this.balance) {
            throw new IllegalArgumentException("Số dư không đủ để thực hiện giao dịch!");
        }
        this.balance += amount;
    }

    /**
     * Phương thức trừu tượng để lấy tên vai trò.
     * Mỗi lớp con (RegularUser, Admin) sẽ phải tự định nghĩa hàm này.
     */
    public abstract String getRoleName();


    public String getUserName() {
        return this.userName;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public String getFullName() {
        return this.fullName;
    }

    public long getBalance() {
        return this.balance;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void lockAccount() {
        this.isActive = false;
    }

    public void unlockAccount() {
        this.isActive = true;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setBalance(long balance) {
        if (balance >= 0) {
            this.balance = balance;
        }else{
            throw new IllegalArgumentException("Lỗi nghiêm trọng: Không thể thiết lập số dư âm!");
        }
    }
}

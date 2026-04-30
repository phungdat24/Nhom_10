package com.nhomX.example.model;

public class User {
    private String userId;
    private String userName;
    private String passWord;
    private String fullName;
    private double balance; // Số dư tk của user
    public User(){

    }
    public User(String userId, String userName, String passWord, String fullName, double balance){
        this.userId = userId;
        this.userName = userName;
        this.passWord = passWord;
        this.fullName = fullName;
        this.balance = balance;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getPassWord() {
        return this.passWord;
    }

    public String getFullName() {
        return this.fullName;
    }

    public double getBalance() {
        return this.balance;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setBalance(double balance) {
        if (balance > 0.0) {
            this.balance = balance;
        }
    }
}

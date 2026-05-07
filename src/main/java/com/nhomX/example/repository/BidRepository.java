package com.nhomX.example.repository;

import java.util.List;

import com.nhomX.example.model.Bid;

// Interface quản lý lượt trả giá:
public interface BidRepository {
    void addBid(Bid bid);// Ghi nhận lượt trả giá mới

    List<Bid> getBitsByItemId(String itemId); // Lấy lịch sử trả giá của Item

    Bid getHighestBid(String itemId); // Tìm lượt trả giá cao nhất hiện tại

    boolean placeBidTransaction(String userId, String itemId, long bidAmount, String bidId);
}
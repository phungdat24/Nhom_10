package com.nhomX.example.repository;

import java.util.List;

import com.nhomX.example.model.BidTransaction;

// Interface quản lý lượt trả giá:
public interface BidRepository {
    void addBid(BidTransaction bidTransaction);// Ghi nhận lượt trả giá mới

    List<BidTransaction> getBidsByAuctionId(String auctionId); // Lấy lịch sử trả giá của Item

    BidTransaction getHighestBid(String auctionId); // Tìm lượt trả giá cao nhất hiện tại

    boolean executeBidTransaction(String userId, String auctionId, long bidAmount, String bidId);

    boolean saveAutoBidConfig(String userId, String auctionId, long maxLimit, long increment);
}
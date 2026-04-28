package com.nhomX.example.controller;

import com.nhomX.example.model.Items;
import com.nhomX.example.networking.AuctionClient;
import com.nhomX.example.repository.ItemRepository;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MainDashBoardController {
    private AuctionClient auctionClient;
    private ItemRepository itemRepository;
    @FXML
    void handleBid(ActionEvent event) { }

    @FXML
    void handleBuy(ActionEvent event) { }

    @FXML
    void handleDashboard(ActionEvent event) { }
    @FXML
    void handleLogin(ActionEvent event) {
        System.out.println("Nút đăng nhập trên dashboard vừa được nhấn!");
    }@FXML
    void handleLiveAuction(ActionEvent event) {
        System.out.println("Nút Live Auction trên dashboard vừa được nhấn!");
    }
    @FXML
    void handleMyAuctions(ActionEvent event) {
        System.out.println("Nút My Auctions trên dashboard vừa được nhấn!");
    }
    @FXML
    void handleSeller(ActionEvent event) {
        System.out.println("Nút Seller trên dashboard vừa được nhấn!");
    }
    @FXML
    void handleProfile(ActionEvent event) {
        System.out.println("Nút Profile trên dashboard vừa được nhấn!");
    }
}

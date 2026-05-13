package com.nhomX.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController extends BaseController implements Initializable {
    public static MainController instance;
    @FXML
    StackPane mainContentArea;

    @Override
    public void initialize(URL locationm, ResourceBundle resources){
        instance = this;
        updateHeaderUI();//cap nhat ten user, so du tu BaseController

        //mac dinh khi vua mo app se chieu trang "dashboard"
        loadView("/com/nhomX.example/fxml/DashboardContent.fxml");
    }

    //ĐÂY LÀ PHẦN QUAN TRỌNG NHẤT: BO NAP NOI DUNG DONG
    public void loadView(String fxmlPath){
        try{
            //1. Tai file fxml con(phan ruot)
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node newNode = loader.load();

            //2. Xoa noi dung cu trong Center
            mainContentArea.getChildren().clear();

            //3. Nap noi dung moi vao
            mainContentArea.getChildren().add(newNode);
        }catch (IOException e){
            e.printStackTrace();
            System.err.println("Lỗi không thể tải trang con: " + fxmlPath);
        }
    }

    // Hàm mới: Nhận một cục giao diện đã có sẵn dữ liệu và hiển thị lên Center
    public void setCenterContent(Node node) {
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(node);
    }

    //Cac ham xu li su kien nut bam tren Sidebar
    @FXML
    @Override
    protected void handleLiveAuction(ActionEvent event){
        loadView("/com/nhomX.example/fxml/LiveAuctionContent.fxml");
    }

    @FXML
    @Override
    protected void handleDashboard(ActionEvent event){
        loadView("/com/nhomX.example/fxml/DashboardContent.fxml");
    }

    @FXML
    @Override
    protected void handleMyAuctions(ActionEvent event){
        loadView("/com/nhomX.example/fxml/MyAuctionsContent.fxml");
    }

    @FXML
    @Override
    protected void handleSeller(ActionEvent event){
        loadView("/com/nhomX.example/fxml/SellerContent.fxml");
    }

}

module com.auction.auctionsystem {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.auction.auctionsystem to javafx.fxml;
    exports com.auction.auctionsystem;
}
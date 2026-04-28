module com.nhomX.example{
    requires javafx.controls;
    requires javafx.fxml;
    opens com.nhomX.example to javafx.fxml;
    exports com.nhomX.example;
    requires java.sql;
}
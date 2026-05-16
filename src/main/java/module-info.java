module com.nhomX.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires java.mail;

    opens com.nhomX.example to javafx.fxml;
    opens com.nhomX.example.controller to javafx.fxml;

    exports com.nhomX.example;
}

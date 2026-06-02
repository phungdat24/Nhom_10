module com.nhomX.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires java.mail;
    requires fontawesomefx;

    opens com.nhomX.example to javafx.fxml;

    exports com.nhomX.example;
    exports com.nhomX.example.manager;

    opens com.nhomX.example.controller.client to javafx.fxml;
    exports com.nhomX.example.controller.client;
    opens com.nhomX.example.controller.shared to javafx.fxml;
    exports com.nhomX.example.controller.shared;
    opens com.nhomX.example.controller.admin to javafx.fxml;
    exports com.nhomX.example.controller.admin;
}

module de.oppermann.jpgrenamer {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.commons.imaging;
    requires java.desktop;


    opens de.oppermann.jpgrenamer to javafx.fxml;
    exports de.oppermann.jpgrenamer;
}
package com.myprojects.kpok2;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.ResourceBundle;
import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.myprojects.kpok2",
    "com.myprojects.kpok2.config",
    "com.myprojects.kpok2.service.navigation"
})
public class Kpok2Application extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(Kpok2Application.class).run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
        fxmlLoader.setControllerFactory(springContext::getBean);
        fxmlLoader.setResources(bundle);
        
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        
        stage.setScene(scene);
        stage.setTitle(springContext.getEnvironment().getProperty("ui.window.title"));
        stage.setWidth(Double.parseDouble(springContext.getEnvironment().getProperty("ui.window.width")));
        stage.setHeight(Double.parseDouble(springContext.getEnvironment().getProperty("ui.window.height")));
        stage.show();
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }

    public static void main(String[] args) {
        // PostgreSQL 18 no longer accepts the legacy IANA identifier used by
        // some Windows/JDK installations. Normalize it before JDBC starts.
        if ("Europe/Kiev".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));
        }
        launch(args);
    }
}

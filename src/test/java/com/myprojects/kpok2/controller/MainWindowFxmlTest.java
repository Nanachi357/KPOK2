package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MainWindowFxmlTest {

    @Test
    void mainWindowStopControlsAndResourcesAreValid() throws Exception {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        assertNotNull(messages.getString("navigation.run.start"));
        assertNotNull(messages.getString("navigation.run.stop.after.current"));
        assertNotNull(messages.getString("navigation.run.stop.now"));
        assertNotNull(messages.getString("navigation.run.status.stopping"));
        assertNotNull(messages.getString("navigation.run.status.stopping.now"));

        try (var stream = getClass().getResourceAsStream("/fxml/main-window.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.MainWindowController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

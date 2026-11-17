package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NavigationConfigFxmlTest {

    @Test
    void navigationSettingsMarkupExplainsUnlimitedAndLargeRuns() throws Exception {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        assertNotNull(messages.getString("navigation.config.iteration.count.help"));

        try (var stream = getClass().getResourceAsStream("/fxml/navigation-config.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.NavigationConfigController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

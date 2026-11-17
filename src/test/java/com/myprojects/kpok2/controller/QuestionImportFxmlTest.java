package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuestionImportFxmlTest {

    @Test
    void importDialogMarkupAndResourcesAreValid() throws Exception {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        assertNotNull(messages.getString("import.json.title"));
        assertNotNull(messages.getString("import.json.description"));
        assertNotNull(messages.getString("import.json.browse"));
        assertNotNull(messages.getString("import.json.button"));

        try (var stream = getClass().getResourceAsStream(
                "/fxml/question-import.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.QuestionImportDialogController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

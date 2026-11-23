package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuestionBackupFxmlTest {

    @Test
    void backupDialogMarkupAndResourcesAreValid() throws Exception {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        assertNotNull(messages.getString("backup.title"));
        assertNotNull(messages.getString("backup.description"));
        assertNotNull(messages.getString("backup.create.button"));
        assertNotNull(messages.getString("backup.restore.button"));

        try (var stream = getClass().getResourceAsStream(
                "/fxml/question-backup.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.QuestionBackupDialogController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuestionExportFxmlTest {

    @Test
    void exportDialogMarkupIsWellFormedAndTargetsExpectedController() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/fxml/question-export.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.QuestionExportDialogController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

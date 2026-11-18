package com.myprojects.kpok2.controller;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuestionLibraryFxmlTest {

    @Test
    void libraryMarkupAndResourcesAreValid() throws Exception {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages");
        List.of(
                "question.library.title",
                "question.library.description",
                "question.library.search.prompt",
                "question.library.column.question",
                "question.library.previous",
                "question.library.next",
                "question.library.loading",
                "question.library.no.results")
                .forEach(key -> assertNotNull(messages.getString(key)));

        try (var stream = getClass().getResourceAsStream(
                "/fxml/question-library.fxml")) {
            assertNotNull(stream);
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream);
            assertEquals(
                    "com.myprojects.kpok2.controller.QuestionLibraryDialogController",
                    document.getDocumentElement().getAttribute("fx:controller"));
        }
    }
}

package com.myprojects.kpok2.service.catalog;

import com.myprojects.kpok2.model.QuizCatalogEntry;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuizCatalogParserTest {

    private final QuizCatalogParser parser = new QuizCatalogParser();

    @Test
    void parsesAndClassifiesMoodleQuizLinksWithoutLocalizedSelectors() throws IOException {
        String html;
        try (var stream = getClass().getResourceAsStream("/fixtures/moodle/quiz-catalog.html")) {
            if (stream == null) {
                throw new IOException("Missing quiz catalog fixture");
            }
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<QuizCatalogEntry> entries = parser.parse(Jsoup.parse(
                html, QuizCatalogService.CATALOG_URL));

        assertEquals(4, entries.size());

        QuizCatalogEntry psychiatry = entries.stream()
                .filter(entry -> entry.courseModuleId() == 173L)
                .findFirst()
                .orElseThrow();
        assertEquals("Крок 3 Психіатрія", psychiatry.title());
        assertEquals("КРОК 3", psychiatry.section());
        assertEquals("KROK 3", psychiatry.krokLevel());
        assertEquals("Психіатрія", psychiatry.specialty());
        assertEquals("uk", psychiatry.language());
        assertEquals(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173",
                psychiatry.url());

        QuizCatalogEntry english = entries.stream()
                .filter(entry -> entry.courseModuleId() == 160L)
                .findFirst()
                .orElseThrow();
        assertEquals("en", english.language());
        assertEquals("Medicine", english.specialty());

        QuizCatalogEntry unmarkedEnglish = entries.stream()
                .filter(entry -> entry.courseModuleId() == 161L)
                .findFirst()
                .orElseThrow();
        assertEquals("en", unmarkedEnglish.language());
        assertEquals("Dentistry", unmarkedEnglish.specialty());
    }
}

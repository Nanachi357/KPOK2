package com.myprojects.kpok2.service.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.QuizCatalogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuizCatalogCacheTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsTheLatestCatalog() {
        QuizCatalogCache cache = new QuizCatalogCache(
                new ObjectMapper(), temporaryDirectory.resolve("quiz-catalog.json"));
        QuizCatalogEntry entry = new QuizCatalogEntry(
                173L,
                "Крок 3 Психіатрія (UA)",
                "КРОК 3",
                "KROK 3",
                "Психіатрія",
                "uk",
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");

        var saved = cache.save(List.of(entry));
        var loaded = cache.load();

        assertFalse(saved.updatedAt().isBlank());
        assertEquals(saved, loaded);
        assertEquals(temporaryDirectory.resolve("quiz-catalog.json"), cache.getCachePath());
    }
}

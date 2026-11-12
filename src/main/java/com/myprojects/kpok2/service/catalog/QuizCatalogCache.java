package com.myprojects.kpok2.service.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.QuizCatalogEntry;
import com.myprojects.kpok2.model.QuizCatalogSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

/**
 * Stores the latest successful catalog refresh outside the repository.
 */
@Slf4j
@Component
public class QuizCatalogCache {

    private static final String CACHE_FILE_NAME = "quiz-catalog.json";

    private final ObjectMapper objectMapper;
    private final Path cachePath;

    @Autowired
    public QuizCatalogCache(ObjectMapper objectMapper) {
        this(objectMapper, Path.of(System.getProperty("user.home"), ".kpok2", CACHE_FILE_NAME));
    }

    QuizCatalogCache(ObjectMapper objectMapper, Path cachePath) {
        this.objectMapper = objectMapper;
        this.cachePath = cachePath;
    }

    public QuizCatalogSnapshot load() {
        if (!Files.exists(cachePath)) {
            return QuizCatalogSnapshot.empty();
        }

        try {
            return objectMapper.readValue(cachePath.toFile(), QuizCatalogSnapshot.class);
        } catch (Exception e) {
            log.warn("Could not read quiz catalog cache {}: {}", cachePath, e.getMessage());
            return QuizCatalogSnapshot.empty();
        }
    }

    public QuizCatalogSnapshot save(List<QuizCatalogEntry> entries) {
        QuizCatalogSnapshot snapshot = new QuizCatalogSnapshot(
                Instant.now().toString(), entries);
        Path temporaryPath = null;

        try {
            Files.createDirectories(cachePath.getParent());
            temporaryPath = Files.createTempFile(
                    cachePath.getParent(), "quiz-catalog-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryPath.toFile(), snapshot);
            moveIntoPlace(temporaryPath);
            return snapshot;
        } catch (IOException e) {
            throw new IllegalStateException("Could not save quiz catalog cache", e);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException e) {
                    log.warn("Could not remove temporary catalog cache {}: {}",
                            temporaryPath, e.getMessage());
                }
            }
        }
    }

    public Path getCachePath() {
        return cachePath;
    }

    private void moveIntoPlace(Path temporaryPath) throws IOException {
        try {
            Files.move(temporaryPath, cachePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryPath, cachePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

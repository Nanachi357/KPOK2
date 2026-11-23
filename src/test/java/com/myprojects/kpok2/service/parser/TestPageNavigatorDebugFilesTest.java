package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.DebugProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestPageNavigatorDebugFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void doesNotReadOrWritePageSourceWhenDiagnosticsAreDisabled() throws Exception {
        DebugProperties properties = properties(false);
        TestPageNavigator navigator = new TestPageNavigator(properties);
        WebDriver driver = mock(WebDriver.class);

        navigator.init();
        navigator.savePageSource(driver, "https://example.test/review.php?sesskey=secret");

        verifyNoInteractions(driver);
        try (var paths = Files.list(temporaryDirectory)) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void redactsSessionKeysFromOptInHtmlAndFilename() throws Exception {
        DebugProperties properties = properties(true);
        TestPageNavigator navigator = new TestPageNavigator(properties);
        WebDriver driver = mock(WebDriver.class);
        when(driver.getPageSource()).thenReturn("""
                <html><body>
                  <form action="/submit.php?sesskey=query-secret&amp;attempt=1">
                    <input value="input-secret" name="sesskey">
                  </form>
                  <a href="/continue?sesskey%3Dencoded-secret%26attempt%3D1">Continue</a>
                  <script>window.config = {"sesskey":"json-secret"};</script>
                </body></html>
                """);

        navigator.init();
        navigator.savePageSource(
                driver,
                "https://example.test/review.php?attempt=1&sesskey=filename-secret");

        List<Path> files;
        try (var paths = Files.list(temporaryDirectory.resolve("html"))) {
            files = paths.toList();
        }
        assertEquals(1, files.size());
        assertFalse(files.get(0).getFileName().toString().contains("filename-secret"));

        String saved = Files.readString(files.get(0), StandardCharsets.UTF_8);
        assertTrue(saved.contains(DebugHtmlSanitizer.REDACTED));
        assertFalse(saved.contains("query-secret"));
        assertFalse(saved.contains("input-secret"));
        assertFalse(saved.contains("encoded-secret"));
        assertFalse(saved.contains("json-secret"));
    }

    private DebugProperties properties(boolean enabled) {
        DebugProperties properties = new DebugProperties();
        properties.setSaveFiles(enabled);
        properties.setOutputDir(temporaryDirectory.toString());
        return properties;
    }
}

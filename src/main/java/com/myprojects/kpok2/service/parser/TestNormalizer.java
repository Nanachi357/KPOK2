package com.myprojects.kpok2.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Component responsible for normalizing text for comparison
 */
@Slf4j
@Component
public class TestNormalizer {

    public static final int CURRENT_VERSION = 2;

    /**
     * Normalizes text for comparison
     * @param text Text to normalize
     * @return Normalized text
     */
    public String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);

        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalizes an answer while ignoring Moodle labels such as "a." or "1)".
     */
    public String normalizeAnswer(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return normalizeText(normalized.replaceFirst(
                "^\\s*[\\p{L}\\p{N}]\\s*[.)]\\s*", ""));
    }

    /**
     * Exact v1 behavior retained only for duplicate detection against existing rows.
     */
    public String normalizeLegacyText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("^[a-z]\\.", "").trim();

        return normalized.toLowerCase()
                .replaceAll("[^а-яa-z0-9іїєґ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

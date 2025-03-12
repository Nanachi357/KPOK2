package com.myprojects.kpok2.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Component responsible for normalizing text for comparison
 */
@Slf4j
@Component
public class TestNormalizer {

    /**
     * Normalizes text for comparison
     * @param text Text to normalize
     * @return Normalized text
     */
    public String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        
        // First apply Unicode normalization (NFD) to handle diacritics
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        
        // Then apply custom normalization rules
        return normalized.toLowerCase()
                .replaceAll("[^а-яa-z0-9іїєґ]", " ") // Include Ukrainian letters
                .replaceAll("\\s+", " ")
                .trim();
    }
} 
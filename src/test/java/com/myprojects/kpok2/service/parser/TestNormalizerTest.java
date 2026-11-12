package com.myprojects.kpok2.service.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestNormalizerTest {

    private final TestNormalizer normalizer = new TestNormalizer();

    @Test
    void preservesLettersFromAnyUnicodeAlphabet() {
        assertEquals(
                "їжак déjà vu 東京 हिंदी 1",
                normalizer.normalizeText("Їжак, déjà vu — 東京 — हिंदी ①"));
    }

    @Test
    void removesUnicodeAnswerLabelsOnlyForAnswers() {
        assertEquals("чотири", normalizer.normalizeAnswer("б. Чотири"));
        assertEquals("чотири", normalizer.normalizeAnswer("2) Чотири"));
        assertEquals("four", normalizer.normalizeAnswer("Ａ． Four"));
        assertEquals("б чотири", normalizer.normalizeText("б. Чотири"));
    }

    @Test
    void retainsLegacyBehaviorForExistingHashLookup() {
        assertNotEquals(
                normalizer.normalizeText("Їжак"),
                normalizer.normalizeLegacyText("Їжак"));
    }
}

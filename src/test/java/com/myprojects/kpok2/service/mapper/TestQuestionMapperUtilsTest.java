package com.myprojects.kpok2.service.mapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestQuestionMapperUtilsTest {

    @Test
    void v2HashIsIndependentOfAnswerOrder() {
        String first = TestQuestionMapperUtils.generateHashV2(
                "question",
                List.of("alpha", "beta"),
                List.of("beta"));
        String second = TestQuestionMapperUtils.generateHashV2(
                "question",
                List.of("beta", "alpha"),
                List.of("beta"));

        assertEquals(first, second);
    }

    @Test
    void legacyHashRemainsCompatibleWithV1Rows() {
        assertEquals(
                "87abdd1484a331c8d8fed704bed10f12",
                TestQuestionMapperUtils.generateLegacyHash(
                        "скільки буде два плюс два",
                        List.of("а. Три", "б. Чотири", "в. П'ять"),
                        "б чотири"));
    }
}

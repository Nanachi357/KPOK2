package com.myprojects.kpok2.service.mapper;

import org.junit.jupiter.api.Test;
import com.myprojects.kpok2.model.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @Test
    void v3HashIncludesContextButPreservesV2ForOrdinaryQuestions() {
        String v2 = TestQuestionMapperUtils.generateHashV2(
                "question", List.of("alpha", "beta"), List.of("beta"));
        String withoutContext = TestQuestionMapperUtils.generateHashV3(
                "", "question", List.of("alpha", "beta"), List.of("beta"));
        String firstContext = TestQuestionMapperUtils.generateHashV3(
                "first case", "question", List.of("alpha", "beta"), List.of("beta"));
        String secondContext = TestQuestionMapperUtils.generateHashV3(
                "second case", "question", List.of("alpha", "beta"), List.of("beta"));

        assertEquals(v2, withoutContext);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstContext, secondContext);
    }

    @Test
    void canonicalIdentityDoesNotDependOnOptionsOrAnswerKey() {
        String first = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question");
        String second = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question");

        assertEquals(first, second);
    }

    @Test
    void richContentChangesCanonicalIdentityWithoutChangingTextOnlyIdentity() {
        String textOnly = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question");
        String blankFingerprint = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question", "");
        String firstImage = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question", "image-one");
        String secondImage = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question", "image-two");

        assertEquals(textOnly, blankFingerprint);
        assertNotEquals(textOnly, firstImage);
        assertNotEquals(firstImage, secondImage);
    }

    @Test
    void revisionIdentityIgnoresOptionOrderButDetectsChangedOptions() {
        String first = TestQuestionMapperUtils.generateRevisionHash(
                "canonical",
                List.of("alpha", "beta"),
                QuestionType.SINGLE_CHOICE);
        String reordered = TestQuestionMapperUtils.generateRevisionHash(
                "canonical",
                List.of("beta", "alpha"),
                QuestionType.SINGLE_CHOICE);
        String changed = TestQuestionMapperUtils.generateRevisionHash(
                "canonical",
                List.of("alpha", "gamma"),
                QuestionType.SINGLE_CHOICE);

        assertEquals(first, reordered);
        assertNotEquals(first, changed);
    }

    @Test
    void richAnswerContentChangesRevisionIdentity() {
        String first = TestQuestionMapperUtils.generateRevisionHash(
                "canonical",
                List.of("alpha", "beta"),
                QuestionType.SINGLE_CHOICE,
                "answer-image-one");
        String changed = TestQuestionMapperUtils.generateRevisionHash(
                "canonical",
                List.of("beta", "alpha"),
                QuestionType.SINGLE_CHOICE,
                "answer-image-two");

        assertNotEquals(first, changed);
    }

    @Test
    void answerKeyIdentityDetectsConflictingCorrectAnswers() {
        String first = TestQuestionMapperUtils.generateAnswerKeyHash(List.of("beta"));
        String same = TestQuestionMapperUtils.generateAnswerKeyHash(List.of("beta"));
        String conflict = TestQuestionMapperUtils.generateAnswerKeyHash(List.of("alpha"));

        assertEquals(first, same);
        assertNotEquals(first, conflict);
    }
}

package com.myprojects.kpok2.service.mapper;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestQuestionMapperTest {

    @Test
    void mapsV5MetadataContextAndAnswerLists() {
        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder()
                .questionText("Question")
                .normalizedText("question")
                .contextText("Case description")
                .normalizedContext("case description")
                .answers(List.of("a. One", "b. Two"))
                .normalizedAnswers(List.of("one", "two"))
                .correctAnswers(List.of("b. Two"))
                .normalizedCorrectAnswers(List.of("two"))
                .correctAnswer("b. Two")
                .normalizedCorrectAnswer("two")
                .normalizationVersion(5)
                .questionType(QuestionType.SINGLE_CHOICE)
                .sourceQuizId(173L)
                .sourceUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=173")
                .sourceLanguage("en")
                .build();

        var entity = new TestQuestionMapperImpl().toEntity(dto);

        assertEquals("[\"a. One\",\"b. Two\"]", entity.getPossibleAnswersJson());
        assertEquals("[\"b. Two\"]", entity.getCorrectAnswersJson());
        assertEquals(QuestionType.SINGLE_CHOICE, entity.getQuestionType());
        assertEquals("Case description", entity.getContextText());
        assertEquals("case description", entity.getNormalizedContext());
        assertEquals(173L, entity.getSourceQuizId());
        assertEquals(
                TestQuestionMapperUtils.generateCanonicalHash(
                        "case description", "question"),
                entity.getQuestionHash());
    }
}

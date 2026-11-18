package com.myprojects.kpok2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.util.JsonConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionLibraryDetailServiceTest {

    @Test
    void assemblesRevisionsKeysConflictsAndOrigins() {
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionRevisionRepository revisions = mock(QuestionRevisionRepository.class);
        AnswerKeyRepository keys = mock(AnswerKeyRepository.class);
        QuestionOriginRepository origins = mock(QuestionOriginRepository.class);
        QuestionLibraryDetailService service = new QuestionLibraryDetailService(
                questions,
                revisions,
                keys,
                origins,
                new JsonConverter(new ObjectMapper()));
        LocalDateTime observedAt = LocalDateTime.of(2026, 11, 18, 10, 0);
        TestQuestion question = TestQuestion.builder()
                .id(7L)
                .questionHash("question-hash")
                .contextText("Context")
                .questionText("Question")
                .questionType(QuestionType.SINGLE_CHOICE)
                .build();
        QuestionRevision revision = QuestionRevision.builder()
                .id(11L)
                .question(question)
                .revisionHash("revision-hash")
                .contextText("Context")
                .questionText("Question")
                .possibleAnswersJson("[\"A\",\"B\"]")
                .questionType(QuestionType.SINGLE_CHOICE)
                .firstSeenAt(observedAt)
                .lastSeenAt(observedAt)
                .build();
        AnswerKey firstKey = answerKey(
                21L, revision, "key-a", "[\"A\"]", observedAt);
        AnswerKey secondKey = answerKey(
                22L, revision, "key-b", "[\"B\"]", observedAt);
        QuestionOrigin origin = QuestionOrigin.builder()
                .question(question)
                .quizId(173L)
                .quizUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=173")
                .language("uk")
                .firstSeenAt(observedAt)
                .lastSeenAt(observedAt)
                .build();
        when(questions.findById(7L)).thenReturn(Optional.of(question));
        when(revisions.findAllByQuestionIdInOrderByQuestionIdAscIdAsc(List.of(7L)))
                .thenReturn(List.of(revision));
        when(keys.findAllByRevisionIdInOrderByRevisionIdAscIdAsc(List.of(11L)))
                .thenReturn(List.of(firstKey, secondKey));
        when(origins.findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                List.of(7L))).thenReturn(List.of(origin));

        var detail = service.findByQuestionId(7L).orElseThrow();

        assertEquals(7L, detail.questionId());
        assertEquals(1, detail.origins().size());
        assertEquals("uk", detail.origins().get(0).language());
        assertEquals(1, detail.revisions().size());
        assertEquals(List.of("A", "B"), detail.revisions().get(0).answers());
        assertTrue(detail.revisions().get(0).answerKeyConflict());
        assertEquals(2, detail.revisions().get(0).answerKeys().size());
        assertEquals(List.of("B"),
                detail.revisions().get(0).answerKeys().get(1).correctAnswers());
    }

    @Test
    void returnsEmptyForUnknownOrMissingId() {
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionLibraryDetailService service = new QuestionLibraryDetailService(
                questions,
                mock(QuestionRevisionRepository.class),
                mock(AnswerKeyRepository.class),
                mock(QuestionOriginRepository.class),
                new JsonConverter(new ObjectMapper()));
        when(questions.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.findByQuestionId(null).isPresent());
        assertFalse(service.findByQuestionId(99L).isPresent());
    }

    private AnswerKey answerKey(
            Long id,
            QuestionRevision revision,
            String hash,
            String answers,
            LocalDateTime observedAt) {
        return AnswerKey.builder()
                .id(id)
                .revision(revision)
                .keyHash(hash)
                .correctAnswersJson(answers)
                .normalizedCorrectAnswersJson(answers.toLowerCase())
                .firstSeenAt(observedAt)
                .lastSeenAt(observedAt)
                .build();
    }
}

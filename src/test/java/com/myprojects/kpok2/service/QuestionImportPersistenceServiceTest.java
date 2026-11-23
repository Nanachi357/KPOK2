package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionImportPersistenceServiceTest {

    @Test
    void createsEveryNormalizedRecordFromAValidatedDocument() {
        Fixture fixture = fixture();
        fixture.stubEmptyDatabaseWithGeneratedIds();

        var result = fixture.service.importDocument(
                QuestionImportServiceTest.validDocument());

        assertEquals(1, result.canonicalQuestionsCreated());
        assertEquals(1, result.revisionsCreated());
        assertEquals(1, result.answerKeysCreated());
        assertEquals(1, result.originsCreated());
        assertEquals(1, result.observationsCreated());
        assertEquals(1, result.attemptsCreated());
        assertEquals(0, result.assetsSkipped());
    }

    @Test
    void mergesAnExistingGraphWithoutReportingNewRecords() {
        Fixture fixture = fixture();
        fixture.stubExistingGraph();

        var result = fixture.service.importDocument(
                QuestionImportServiceTest.validDocument());

        assertEquals(0, result.canonicalQuestionsCreated());
        assertEquals(1, result.canonicalQuestionsMerged());
        assertEquals(0, result.revisionsCreated());
        assertEquals(1, result.revisionsMerged());
        assertEquals(0, result.answerKeysCreated());
        assertEquals(1, result.answerKeysMerged());
        assertEquals(0, result.originsCreated());
        assertEquals(1, result.originsMerged());
        assertEquals(0, result.observationsCreated());
        assertEquals(1, result.observationsMerged());
        assertEquals(0, result.attemptsCreated());
        assertEquals(1, result.attemptsMerged());
    }

    @Test
    void serializesConcurrentImportsThroughTransactionCompletion() throws Exception {
        QuestionImportService importService = mock(QuestionImportService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        var document = emptyDocument();
        when(importService.inspect(document)).thenReturn(null);
        AtomicInteger activeTransactions = new AtomicInteger();
        AtomicInteger maximumActiveTransactions = new AtomicInteger();
        when(transactions.execute(any())).thenAnswer(invocation -> {
            int active = activeTransactions.incrementAndGet();
            maximumActiveTransactions.accumulateAndGet(active, Math::max);
            try {
                @SuppressWarnings("unchecked")
                org.springframework.transaction.support.TransactionCallback<Object> callback =
                        invocation.getArgument(0);
                Object result = callback.doInTransaction(mock(TransactionStatus.class));
                Thread.sleep(20);
                return result;
            } finally {
                activeTransactions.decrementAndGet();
            }
        });
        QuestionImportPersistenceService service = new QuestionImportPersistenceService(
                importService,
                mock(TestQuestionRepository.class),
                mock(QuestionRevisionRepository.class),
                mock(AnswerKeyRepository.class),
                mock(QuestionOriginRepository.class),
                mock(QuestionObservationRepository.class),
                mock(CollectionAttemptRepository.class),
                transactions);
        int requestCount = 20;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.importDocument(document);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, maximumActiveTransactions.get());
    }

    private QuestionExportDocument emptyDocument() {
        return new QuestionExportDocument(
                QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-23T18:00:00Z"),
                new QuestionExportDocument.ExportFilter(
                        List.of(), List.of(), List.of(), false, true, ""),
                new QuestionExportDocument.ExportSummary(
                        0, 0, 0, 0, 0, 0, 0, 0),
                List.of(),
                List.of());
    }

    private Fixture fixture() {
        QuestionImportService importService = mock(QuestionImportService.class);
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionRevisionRepository revisions = mock(
                QuestionRevisionRepository.class);
        AnswerKeyRepository keys = mock(AnswerKeyRepository.class);
        QuestionOriginRepository origins = mock(QuestionOriginRepository.class);
        QuestionObservationRepository observations = mock(
                QuestionObservationRepository.class);
        CollectionAttemptRepository attempts = mock(
                CollectionAttemptRepository.class);
        TransactionTemplate transactions = immediateTransactions();
        var document = QuestionImportServiceTest.validDocument();
        when(importService.inspect(document)).thenReturn(null);
        return new Fixture(
                new QuestionImportPersistenceService(
                        importService,
                        questions,
                        revisions,
                        keys,
                        origins,
                        observations,
                        attempts,
                        transactions),
                questions,
                revisions,
                keys,
                origins,
                observations,
                attempts);
    }

    private TransactionTemplate immediateTransactions() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            org.springframework.transaction.support.TransactionCallback<Object> callback =
                    invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private record Fixture(
            QuestionImportPersistenceService service,
            TestQuestionRepository questions,
            QuestionRevisionRepository revisions,
            AnswerKeyRepository keys,
            QuestionOriginRepository origins,
            QuestionObservationRepository observations,
            CollectionAttemptRepository attempts) {

        @SuppressWarnings("unchecked")
        private void stubEmptyDatabaseWithGeneratedIds() {
            when(questions.findAllByQuestionHashIn(any())).thenReturn(List.of());
            when(questions.saveAll(any())).thenAnswer(invocation -> {
                List<TestQuestion> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(101L));
                return values;
            });
            when(origins
                    .findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                            List.of(101L)))
                    .thenReturn(List.of());
            when(origins.saveAll(any())).thenAnswer(invocation -> {
                List<QuestionOrigin> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(111L));
                return values;
            });
            when(revisions.findAllByQuestionIdInOrderByQuestionIdAscIdAsc(
                    List.of(101L))).thenReturn(List.of());
            when(revisions.saveAll(any())).thenAnswer(invocation -> {
                List<QuestionRevision> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(201L));
                return values;
            });
            when(keys.findAllByRevisionIdInOrderByRevisionIdAscIdAsc(
                    List.of(201L))).thenReturn(List.of());
            when(keys.saveAll(any())).thenAnswer(invocation -> {
                List<AnswerKey> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(301L));
                return values;
            });
            when(observations
                    .findAllByRevisionIdInOrderByRevisionIdAscAnswerKeyIdAscIdAsc(
                            List.of(201L)))
                    .thenReturn(List.of());
            when(observations.saveAll(any())).thenAnswer(invocation -> {
                List<QuestionObservation> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(401L));
                return values;
            });
            when(attempts.findAllByQuizIdInOrderByQuizIdAscIdAsc(any()))
                    .thenReturn(List.of());
            when(attempts.saveAll(any())).thenAnswer(invocation -> {
                List<CollectionAttempt> values = invocation.getArgument(0);
                values.forEach(value -> value.setId(501L));
                return values;
            });
        }

        private void stubExistingGraph() {
            var document = QuestionImportServiceTest.validDocument();
            var sourceQuestion = document.questions().get(0);
            var sourceRevision = sourceQuestion.revisions().get(0);
            var sourceKey = sourceRevision.answerKeys().get(0);
            var sourceOrigin = sourceQuestion.origins().get(0);
            var sourceObservation = sourceKey.observations().get(0);
            var sourceAttempt = document.attempts().get(0);

            TestQuestion question = TestQuestion.builder()
                    .id(101L)
                    .questionHash(sourceQuestion.questionHash())
                    .questionText(sourceQuestion.questionText())
                    .normalizedText(sourceQuestion.normalizedText())
                    .contextText(sourceQuestion.contextText())
                    .normalizedContext(sourceQuestion.normalizedContext())
                    .possibleAnswersJson("[\"Alpha\",\"Beta\"]")
                    .correctAnswersJson("[\"Beta\"]")
                    .correctAnswer("Beta")
                    .normalizedCorrectAnswer("beta")
                    .normalizationVersion(sourceQuestion.normalizationVersion())
                    .questionType(sourceQuestion.questionType())
                    .sourceQuizId(sourceOrigin.quizId())
                    .sourceUrl(sourceOrigin.quizUrl())
                    .sourceLanguage(sourceOrigin.language())
                    .parsedAt(sourceQuestion.parsedAt())
                    .build();
            QuestionRevision revision = QuestionRevision.builder()
                    .id(201L)
                    .question(question)
                    .revisionHash(sourceRevision.revisionHash())
                    .contextText(sourceRevision.contextText())
                    .questionText(sourceRevision.questionText())
                    .contextHtml(sourceRevision.contextHtml())
                    .questionHtml(sourceRevision.questionHtml())
                    .possibleAnswersJson("[\"Alpha\",\"Beta\"]")
                    .normalizedAnswersJson("[\"alpha\",\"beta\"]")
                    .answerHtmlJson("[\"<p>Alpha</p>\",\"<p>Beta</p>\"]")
                    .questionType(sourceRevision.questionType())
                    .firstSeenAt(sourceRevision.firstSeenAt())
                    .lastSeenAt(sourceRevision.lastSeenAt())
                    .build();
            AnswerKey key = AnswerKey.builder()
                    .id(301L)
                    .revision(revision)
                    .keyHash(sourceKey.keyHash())
                    .correctAnswersJson("[\"Beta\"]")
                    .normalizedCorrectAnswersJson("[\"beta\"]")
                    .firstSeenAt(sourceKey.firstSeenAt())
                    .lastSeenAt(sourceKey.lastSeenAt())
                    .build();
            QuestionOrigin origin = QuestionOrigin.builder()
                    .id(111L)
                    .question(question)
                    .quizId(sourceOrigin.quizId())
                    .quizUrl(sourceOrigin.quizUrl())
                    .language(sourceOrigin.language())
                    .firstSeenAt(sourceOrigin.firstSeenAt())
                    .lastSeenAt(sourceOrigin.lastSeenAt())
                    .build();
            QuestionObservation observation = QuestionObservation.builder()
                    .id(401L)
                    .question(question)
                    .revision(revision)
                    .answerKey(key)
                    .quizId(sourceObservation.quizId())
                    .quizUrl(sourceObservation.quizUrl())
                    .sourceAttemptId(sourceObservation.sourceAttemptId())
                    .language(sourceObservation.language())
                    .firstSeenAt(sourceObservation.firstSeenAt())
                    .lastSeenAt(sourceObservation.lastSeenAt())
                    .observationCount(sourceObservation.observationCount())
                    .build();
            CollectionAttempt attempt = CollectionAttempt.builder()
                    .id(501L)
                    .quizId(sourceAttempt.quizId())
                    .sourceAttemptId(sourceAttempt.sourceAttemptId())
                    .quizUrl(sourceAttempt.quizUrl())
                    .reviewUrl(sourceAttempt.reviewUrl())
                    .firstCollectedAt(sourceAttempt.firstCollectedAt())
                    .lastCollectedAt(sourceAttempt.lastCollectedAt())
                    .collectionRuns(sourceAttempt.collectionRuns())
                    .pageCount(sourceAttempt.pageCount())
                    .successfulPageCount(sourceAttempt.successfulPageCount())
                    .discoveredQuestions(sourceAttempt.discoveredQuestions())
                    .parsedQuestions(sourceAttempt.parsedQuestions())
                    .initialNewQuestions(sourceAttempt.initialNewQuestions())
                    .latestNewQuestions(sourceAttempt.latestNewQuestions())
                    .latestDuplicateQuestions(
                            sourceAttempt.latestDuplicateQuestions())
                    .rejectedQuestions(sourceAttempt.rejectedQuestions())
                    .complete(sourceAttempt.complete())
                    .lastError(sourceAttempt.lastError())
                    .build();

            when(questions.findAllByQuestionHashIn(any()))
                    .thenReturn(List.of(question));
            when(origins
                    .findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                            List.of(101L)))
                    .thenReturn(List.of(origin));
            when(revisions.findAllByQuestionIdInOrderByQuestionIdAscIdAsc(
                    List.of(101L))).thenReturn(List.of(revision));
            when(keys.findAllByRevisionIdInOrderByRevisionIdAscIdAsc(
                    List.of(201L))).thenReturn(List.of(key));
            when(observations
                    .findAllByRevisionIdInOrderByRevisionIdAscAnswerKeyIdAscIdAsc(
                            List.of(201L)))
                    .thenReturn(List.of(observation));
            when(attempts.findAllByQuizIdInOrderByQuizIdAscIdAsc(any()))
                    .thenReturn(List.of(attempt));
        }
    }
}

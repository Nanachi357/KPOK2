package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionRevisionAsset;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.service.QuestionAssetFetchService;
import com.myprojects.kpok2.service.QuestionExportService;
import com.myprojects.kpok2.service.QuestionImportService;
import com.myprojects.kpok2.service.QuestionImportPersistenceService;
import com.myprojects.kpok2.service.QuestionOriginService;
import com.myprojects.kpok2.service.QuestionVersionService;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperImpl;
import com.myprojects.kpok2.service.parser.TestNormalizer;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import com.myprojects.kpok2.service.parser.TestQuestionParser;
import com.myprojects.kpok2.util.JsonConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=update",
                "logging.level.com.myprojects.kpok2=INFO"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "KPOK2_POSTGRES_TEST", matches = "true")
class RichContentPostgresIntegrationTest {

    static {
        if ("Europe/Kiev".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));
        }
    }

    @Autowired
    private TestQuestionRepository questionRepository;

    @Autowired
    private QuestionRevisionRepository revisionRepository;

    @Autowired
    private QuestionAssetRepository assetRepository;

    @Autowired
    private QuestionRevisionAssetRepository revisionAssetRepository;

    @Autowired
    private AnswerKeyRepository answerKeyRepository;

    @Autowired
    private QuestionObservationRepository observationRepository;

    @Autowired
    private QuestionOriginRepository originRepository;

    @Autowired
    private CollectionAttemptRepository attemptRepository;

    @Test
    void pagesAndSearchesCanonicalQuestionsByAnswerContent() {
        LocalDateTime now = LocalDateTime.now();
        String unique = UUID.randomUUID().toString().replace("-", "");
        String answerToken = "answer-" + unique;
        TestQuestion question = questionRepository.save(TestQuestion.builder()
                .questionText("Library integration question")
                .normalizedText("library integration question")
                .possibleAnswersJson("[\"Ordinary answer\"]")
                .correctAnswersJson("[\"Ordinary answer\"]")
                .correctAnswer("Ordinary answer")
                .normalizedCorrectAnswer("ordinary answer")
                .normalizationVersion(5)
                .questionType(QuestionType.SINGLE_CHOICE)
                .questionHash(unique)
                .parsedAt(now)
                .build());
        QuestionRevision revision = revisionRepository.save(QuestionRevision.builder()
                .question(question)
                .revisionHash(unique)
                .questionText(question.getQuestionText())
                .possibleAnswersJson("[\"" + answerToken + "\"]")
                .normalizedAnswersJson("[\"" + answerToken + "\"]")
                .answerHtmlJson("[]")
                .questionType(QuestionType.SINGLE_CHOICE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        answerKeyRepository.save(AnswerKey.builder()
                .revision(revision)
                .keyHash(unique)
                .correctAnswersJson("[\"" + answerToken + "\"]")
                .normalizedCorrectAnswersJson("[\"" + answerToken + "\"]")
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        answerKeyRepository.save(AnswerKey.builder()
                .revision(revision)
                .keyHash(unique + "conflict")
                .correctAnswersJson("[\"Alternative answer\"]")
                .normalizedCorrectAnswersJson("[\"alternative answer\"]")
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        long quizId = 900_000L + question.getId();
        originRepository.save(QuestionOrigin.builder()
                .question(question)
                .quizId(quizId)
                .quizUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=" + quizId)
                .language("uk")
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        answerKeyRepository.flush();

        var page = questionRepository.findLibraryPage(
                answerToken,
                quizId,
                "uk",
                QuestionType.SINGLE_CHOICE.name(),
                true,
                PageRequest.of(0, 10));
        var wrongLanguage = questionRepository.findLibraryPage(
                answerToken,
                quizId,
                "en",
                QuestionType.SINGLE_CHOICE.name(),
                true,
                PageRequest.of(0, 10));
        var literalWildcard = questionRepository.findLibraryPage(
                "%_",
                quizId,
                "uk",
                QuestionType.SINGLE_CHOICE.name(),
                true,
                PageRequest.of(0, 10));
        var revisionHash = questionRepository.findLibraryPage(
                unique,
                quizId,
                "uk",
                QuestionType.SINGLE_CHOICE.name(),
                true,
                PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(question.getId(), page.getContent().get(0).getId());
        assertTrue(wrongLanguage.isEmpty());
        assertTrue(literalWildcard.isEmpty());
        assertEquals(question.getId(), revisionHash.getContent().get(0).getId());
    }

    @Test
    void storesJsonbHtmlAndByteaWithARevisionPlacement() {
        LocalDateTime now = LocalDateTime.now();
        String unique = UUID.randomUUID().toString().replace("-", "");
        TestQuestion question = questionRepository.save(TestQuestion.builder()
                .questionText("Rich integration question")
                .normalizedText("rich integration question")
                .possibleAnswersJson("[\"One\",\"Two\"]")
                .correctAnswersJson("[\"Two\"]")
                .correctAnswer("Two")
                .normalizedCorrectAnswer("two")
                .normalizationVersion(5)
                .questionType(QuestionType.SINGLE_CHOICE)
                .questionHash(unique)
                .parsedAt(now)
                .build());
        QuestionRevision revision = revisionRepository.save(QuestionRevision.builder()
                .question(question)
                .revisionHash(unique)
                .questionText(question.getQuestionText())
                .questionHtml("<p>Rich <strong>question</strong></p>")
                .possibleAnswersJson(question.getPossibleAnswersJson())
                .normalizedAnswersJson("[\"one\",\"two\"]")
                .answerHtmlJson("[\"<span>One</span>\",\"<span>Two</span>\"]")
                .questionType(QuestionType.SINGLE_CHOICE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47};
        QuestionAsset asset = assetRepository.save(QuestionAsset.builder()
                .contentHash(unique + unique)
                .mediaType("image/png")
                .originalFileName("integration.png")
                .contentLength(content.length)
                .content(content)
                .createdAt(now)
                .build());
        revisionAssetRepository.save(QuestionRevisionAsset.builder()
                .revision(revision)
                .asset(asset)
                .referenceKey("QUESTION:-1:0")
                .contentLocation(QuestionContentLocation.QUESTION)
                .answerIndex(-1)
                .sequenceIndex(0)
                .sourceUrl("embedded:" + unique)
                .altText("Integration diagram")
                .fetchStatus(AssetFetchStatus.STORED)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        revisionAssetRepository.flush();

        var stored = revisionAssetRepository
                .findAllByRevisionIdOrderByContentLocationAscAnswerIndexAscSequenceIndexAsc(
                        revision.getId());

        assertEquals(1, stored.size());
        assertEquals("<p>Rich <strong>question</strong></p>",
                stored.get(0).getRevision().getQuestionHtml());
        assertArrayEquals(content, stored.get(0).getAsset().getContent());
        assertTrue(stored.get(0).getAsset().getContentHash().length() == 64);
    }

    @Test
    void exportsAndSerializesTheCurrentPostgresDataset() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        QuestionExportService exportService = new QuestionExportService(
                questionRepository,
                revisionRepository,
                answerKeyRepository,
                originRepository,
                observationRepository,
                revisionAssetRepository,
                attemptRepository,
                new JsonConverter(objectMapper),
                objectMapper);

        QuestionExportDocument document = exportService.buildExport(
                QuestionExportFilter.all());
        String json = objectMapper.writeValueAsString(document);
        QuestionImportService importService = new QuestionImportService(objectMapper);
        var importPreview = importService.inspect(document);
        long questionCountBeforeImport = questionRepository.count();
        long revisionCountBeforeImport = revisionRepository.count();
        long keyCountBeforeImport = answerKeyRepository.count();
        long originCountBeforeImport = originRepository.count();
        long observationCountBeforeImport = observationRepository.count();
        var importResult = new QuestionImportPersistenceService(
                importService,
                questionRepository,
                revisionRepository,
                answerKeyRepository,
                originRepository,
                observationRepository,
                attemptRepository)
                .importDocument(document);

        assertEquals(QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                document.schemaVersion());
        assertEquals(document.questions().size(),
                document.summary().canonicalQuestions());
        assertEquals(questionRepository.count(), document.questions().size());
        assertEquals(document.summary(), importPreview.contents());
        assertEquals(0, importResult.canonicalQuestionsCreated());
        assertEquals(document.summary().canonicalQuestions(),
                importResult.canonicalQuestionsMerged());
        assertEquals(0, importResult.revisionsCreated());
        assertEquals(document.summary().revisions(), importResult.revisionsMerged());
        assertEquals(0, importResult.answerKeysCreated());
        assertEquals(document.summary().answerKeys(), importResult.answerKeysMerged());
        assertEquals(questionCountBeforeImport, questionRepository.count());
        assertEquals(revisionCountBeforeImport, revisionRepository.count());
        assertEquals(keyCountBeforeImport, answerKeyRepository.count());
        assertEquals(originCountBeforeImport, originRepository.count());
        assertEquals(observationCountBeforeImport, observationRepository.count());
        assertTrue(json.contains("\"schemaVersion\":\"kpok2-question-export-v1\""));
        assertTrue(json.contains("\"questions\""));
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "KPOK2_REPLAY_SAVED_REVIEWS",
            matches = "true")
    void replaysLatestSavedReviewForEveryKnownQuizWithoutDuplicates() throws Exception {
        Pattern quizPattern = Pattern.compile("_cmid_(\\d+)_");
        Pattern attemptPattern = Pattern.compile("_attempt_(\\d+)_");
        Map<Long, Path> latestByQuiz;
        try (var paths = Files.list(Path.of("debug_output", "html"))) {
            latestByQuiz = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> quizPattern.matcher(path.getFileName().toString()).find())
                    .collect(Collectors.toMap(
                            path -> capturedLong(quizPattern, path),
                            Function.identity(),
                            (first, second) -> Comparator
                                    .comparing((Path path) -> path.getFileName().toString())
                                    .compare(first, second) >= 0 ? first : second));
        }
        assertEquals(6, latestByQuiz.size());

        TestQuestionParser parser = new TestQuestionParser(new TestNormalizer());
        QuestionAssetFetchService assetFetchService = new QuestionAssetFetchService();
        QuestionVersionService versionService = new QuestionVersionService(
                revisionRepository,
                answerKeyRepository,
                observationRepository,
                assetRepository,
                revisionAssetRepository,
                new JsonConverter(new ObjectMapper()));
        TestQuestionService questionService = new TestQuestionService(
                questionRepository,
                new TestQuestionMapperImpl(),
                new TestParsingStatistics(),
                new QuestionOriginService(originRepository),
                versionService);

        int parsedCount = 0;
        int newCanonical = 0;
        int newRevisions = 0;
        int newKeys = 0;
        for (var entry : latestByQuiz.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            long quizId = entry.getKey();
            String viewUrl = "https://test.testcentr.org.ua/mod/quiz/view.php?id=" + quizId;
            var questions = parser.parseQuestions(
                    Jsoup.parse(entry.getValue().toFile(), "UTF-8", viewUrl),
                    viewUrl,
                    quizId);
            assetFetchService.prepareAssets(questions, null);
            String attemptId = Long.toString(capturedLong(attemptPattern, entry.getValue()));
            questions.forEach(question -> question.setSourceAttemptId(attemptId));
            TestQuestionService.SaveResult result = questionService.saveQuestions(questions);
            parsedCount += questions.size();
            newCanonical += result.newCanonicalQuestions().size();
            newRevisions += result.newRevisions();
            newKeys += result.newAnswerKeys();
        }

        assertEquals(650, parsedCount);
        assertEquals(0, newCanonical);
        assertEquals(0, newRevisions);
        assertEquals(0, newKeys);
        assertEquals(648, questionRepository.count());
        assertEquals(650, revisionRepository.count());
        assertTrue(revisionRepository.findAll().stream()
                .allMatch(revision -> revision.getQuestionHtml() != null
                        && !revision.getQuestionHtml().isBlank()));
        assertEquals(0, assetRepository.count());
        assertEquals(0, revisionAssetRepository.count());
    }

    private long capturedLong(Pattern pattern, Path path) {
        var matcher = pattern.matcher(path.getFileName().toString());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Expected identifier in " + path.getFileName());
        }
        return Long.parseLong(matcher.group(1));
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.myprojects.kpok2.model")
@EnableJpaRepositories(basePackages = "com.myprojects.kpok2.repository")
class RichContentJpaTestApplication {
}

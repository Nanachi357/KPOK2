package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.parser.TestNormalizer;
import com.myprojects.kpok2.service.parser.TestQuestionParser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAssetFetchServiceTest {

    @Test
    void decodesDeduplicatesAndRewritesEmbeddedImages() throws IOException {
        TestQuestionParser parser = new TestQuestionParser(new TestNormalizer());
        ParsedTestQuestionDto question = parser.parseQuestions(
                        Jsoup.parse(
                                fixture("review-question-rich-content.html"),
                                "https://moodle.example/mod/quiz/review.php?attempt=1"),
                        "https://moodle.example/mod/quiz/view.php?id=10",
                        10L)
                .get(0);

        new QuestionAssetFetchService().prepareAssets(List.of(question), null);

        assertEquals(3, question.getAssets().size());
        assertTrue(question.getAssets().stream()
                .allMatch(asset -> asset.getFetchStatus() == AssetFetchStatus.STORED));
        assertEquals(1, question.getAssets().stream()
                .map(asset -> asset.getContentHash())
                .distinct()
                .count());
        assertTrue(question.getAssets().stream()
                .allMatch(asset -> asset.getContentHash().length() == 64));
        assertTrue(question.getAssets().stream()
                .allMatch(asset -> asset.getSourceUrl().startsWith("embedded:")));
        assertTrue(question.getContextHtml().contains("src=\"kpok2-asset:"));
        assertTrue(question.getQuestionHtml().contains("src=\"kpok2-asset:"));
        assertFalse(question.getQuestionHtml().contains("data-kpok2-ref"));
        assertFalse(question.getQuestionHtml().contains("data:image/png"));
        assertNotNull(question.getCanonicalContentFingerprint());
        assertNotNull(question.getRevisionContentFingerprint());
        assertEquals(64, question.getCanonicalContentFingerprint().length());
        assertEquals(64, question.getRevisionContentFingerprint().length());
        assertTrue(question.getNormalizedCorrectAnswers().get(0).contains(
                "|content:"));
    }

    @Test
    void removesMalformedEmbeddedPayloadFromPersistedHtml() {
        ParsedQuestionAssetDto asset = ParsedQuestionAssetDto.builder()
                .referenceId("asset-bad")
                .contentLocation(QuestionContentLocation.QUESTION)
                .answerIndex(-1)
                .sequenceIndex(0)
                .sourceUrl("data:image/png;base64,not-valid-base64!")
                .altText("Broken")
                .build();
        ParsedTestQuestionDto question = ParsedTestQuestionDto.builder()
                .questionHtml("<p>Diagram <img data-kpok2-ref=\"asset-bad\" "
                        + "src=\"data:image/png;base64,not-valid-base64!\"></p>")
                .answerHtml(List.of())
                .assets(List.of(asset))
                .build();

        new QuestionAssetFetchService().prepareAssets(List.of(question), null);

        assertEquals(AssetFetchStatus.FAILED, asset.getFetchStatus());
        assertEquals("embedded:unavailable", asset.getSourceUrl());
        assertFalse(question.getQuestionHtml().contains("data:image"));
        assertFalse(question.getQuestionHtml().contains("data-kpok2-ref"));
    }

    @Test
    void bindsRichContentToCorrectOptionIndependentOfDisplayOrder() {
        ParsedTestQuestionDto original = imageAnswerQuestion(false);
        ParsedTestQuestionDto reordered = imageAnswerQuestion(true);
        QuestionAssetFetchService service = new QuestionAssetFetchService();

        service.prepareAssets(List.of(original, reordered), null);

        assertEquals(
                original.getRevisionContentFingerprint(),
                reordered.getRevisionContentFingerprint());
        assertEquals(
                original.getNormalizedCorrectAnswers(),
                reordered.getNormalizedCorrectAnswers());
        assertTrue(original.getNormalizedCorrectAnswers().get(0).contains("|content:"));
    }

    private ParsedTestQuestionDto imageAnswerQuestion(boolean reordered) {
        String firstData = "data:image/png;base64,AQID";
        String secondData = "data:image/png;base64,BAUG";
        String[] sources = reordered
                ? new String[]{secondData, firstData}
                : new String[]{firstData, secondData};
        int correctIndex = reordered ? 1 : 0;
        List<ParsedQuestionAssetDto> assets = java.util.stream.IntStream.range(0, 2)
                .mapToObj(index -> ParsedQuestionAssetDto.builder()
                        .referenceId("answer-" + index)
                        .contentLocation(QuestionContentLocation.ANSWER)
                        .answerIndex(index)
                        .sequenceIndex(0)
                        .sourceUrl(sources[index])
                        .altText("")
                        .build())
                .toList();
        return ParsedTestQuestionDto.builder()
                .normalizedAnswers(List.of("", ""))
                .normalizedCorrectAnswers(List.of(""))
                .normalizedCorrectAnswer("")
                .correctAnswerIndices(List.of(correctIndex))
                .answerHtml(List.of(
                        "<img data-kpok2-ref=\"answer-0\" src=\"" + sources[0] + "\">",
                        "<img data-kpok2-ref=\"answer-1\" src=\"" + sources[1] + "\">"))
                .assets(assets)
                .build();
    }

    private String fixture(String fixtureName) throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/fixtures/moodle/" + fixtureName)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + fixtureName);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

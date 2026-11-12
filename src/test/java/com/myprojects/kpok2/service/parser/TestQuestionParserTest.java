package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestQuestionParserTest {

    @Test
    void parsesSyntheticUkrainianReviewQuestion() throws IOException {
        TestQuestionParser parser = new TestQuestionParser(new TestNormalizer());
        String html;
        try (var stream = getClass().getResourceAsStream(
                "/fixtures/moodle/review-question-uk.html")) {
            if (stream == null) {
                throw new IOException("Missing review question fixture");
            }
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var questions = parser.parseQuestions(Jsoup.parse(html));

        assertEquals(1, questions.size());
        ParsedTestQuestionDto question = questions.get(0);
        assertEquals("Скільки буде два плюс два?", question.getQuestionText());
        assertEquals(3, question.getAnswers().size());
        assertEquals("б. Чотири", question.getCorrectAnswer());
        assertEquals(List.of("б. Чотири"), question.getCorrectAnswers());
        assertEquals(QuestionType.SINGLE_CHOICE, question.getQuestionType());
        assertEquals("uk", question.getSourceLanguage());
        assertEquals(TestNormalizer.CURRENT_VERSION, question.getNormalizationVersion());
    }

    @Test
    void parsesEnglishSingleChoiceWithoutDependingOnFeedbackPrefix() throws IOException {
        ParsedTestQuestionDto question = parseOne("review-question-en-single.html");

        assertEquals(QuestionType.SINGLE_CHOICE, question.getQuestionType());
        assertEquals(List.of("b. Four"), question.getCorrectAnswers());
        assertEquals("en", question.getSourceLanguage());
        assertEquals(173L, question.getSourceQuizId());
        assertEquals(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173",
                question.getSourceUrl());
    }

    @Test
    void parsesAllCorrectAnswersFromMultipleChoice() throws IOException {
        ParsedTestQuestionDto question = parseOne("review-question-en-multiple.html");

        assertEquals(QuestionType.MULTIPLE_CHOICE, question.getQuestionType());
        assertEquals(List.of("b. Beta", "c. Gamma"), question.getCorrectAnswers());
        assertEquals("b. Beta | c. Gamma", question.getCorrectAnswer());
    }

    @Test
    void recognizesTrueFalseQuestion() throws IOException {
        ParsedTestQuestionDto question = parseOne("review-question-en-truefalse.html");

        assertEquals(QuestionType.TRUE_FALSE, question.getQuestionType());
        assertEquals(List.of("b. False"), question.getCorrectAnswers());
    }

    @Test
    void parsesRealMoodleAnswerMarkupWithoutLosingOptionLabels() throws IOException {
        ParsedTestQuestionDto question = parseOne("review-question-uk-real.html");

        assertEquals(5, question.getAnswers().size());
        assertEquals("a. Нервова анорексія", question.getAnswers().get(0));
        assertEquals(List.of("e. Нервова булімія"), question.getCorrectAnswers());
    }

    @Test
    void matchesCorrectAnswerInLanguageWithoutWordSpaces() throws IOException {
        ParsedTestQuestionDto question = parseOne("review-question-ja-single.html");

        assertEquals("ja", question.getSourceLanguage());
        assertEquals(List.of("b. 東京"), question.getCorrectAnswers());
    }

    private ParsedTestQuestionDto parseOne(String fixtureName) throws IOException {
        TestQuestionParser parser = new TestQuestionParser(new TestNormalizer());
        String html;
        try (var stream = getClass().getResourceAsStream("/fixtures/moodle/" + fixtureName)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + fixtureName);
            }
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var questions = parser.parseQuestions(
                Jsoup.parse(html),
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173",
                173L);
        assertEquals(1, questions.size());
        return questions.get(0);
    }
}

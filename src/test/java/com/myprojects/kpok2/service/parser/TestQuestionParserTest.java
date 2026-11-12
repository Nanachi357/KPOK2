package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    }
}

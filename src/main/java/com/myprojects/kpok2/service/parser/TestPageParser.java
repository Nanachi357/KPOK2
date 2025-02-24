package com.myprojects.kpok2.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TestPageParser {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public List<ParsedTestQuestionDto> parseAllPages(String initialUrl) {
        List<ParsedTestQuestionDto> allQuestions = new ArrayList<>();
        String currentUrl = initialUrl;
        int[] expectedRanges = {50, 100, 150};

        for (int i = 0; i < expectedRanges.length; i++) {
            Document doc = getPage(currentUrl);
            List<ParsedTestQuestionDto> pageQuestions = parseTestPage(doc, expectedRanges[i]);
            allQuestions.addAll(pageQuestions);

            if (i < expectedRanges.length - 1) {
                currentUrl = getNextPageUrl(doc);
                if (currentUrl == null) {
                    throw new TestParsingException("Next page link not found");
                }
            }
        }

        return allQuestions;
    }

    public List<ParsedTestQuestionDto> parseTestPage(Document doc, int expectedQuestionsCount) {
        Elements questionDivs = doc.select("div.que.multichoice.deferredfeedback");
        List<ParsedTestQuestionDto> questions = new ArrayList<>();

        for (Element questionDiv : questionDivs) {
            try {
                questions.add(parseQuestion(questionDiv));
            } catch (Exception e) {
                throw new TestParsingException("Failed to parse question", e);
            }
        }

        if (questions.size() != expectedQuestionsCount) {
            log.warn("Found {} questions, expected {}",
                    questions.size(), expectedQuestionsCount);
        }

        return questions;
    }

    public ParsedTestQuestionDto parseQuestion(Element questionDiv) {
        String questionText = questionDiv.select("div.qtext").text();

        List<String> answers = questionDiv.select("div.answer div.flex-fill")
                .stream()
                .map(Element::text)
                .collect(Collectors.toList());

        String correctAnswer = questionDiv.select("div.rightanswer")
                .text()
                .replace("Правильна відповідь:", "")
                .trim();

        return ParsedTestQuestionDto.builder()
                .questionText(questionText)
                .answers(answers)
                .correctAnswer(correctAnswer)
                .build();
    }

    public String getNextPageUrl(Document doc) {
        Element nextLink = doc.select("a.mod_quiz-next-nav").first();
        return nextLink != null ? nextLink.attr("href") : null;
    }

    private Document getPage(String url) {
        try {
            return Jsoup.connect(url)
                    .timeout((int) TIMEOUT.toMillis())
                    .get();
        } catch (IOException e) {
            throw new TestParsingException("Failed to fetch page: " + url, e);
        }
    }
}
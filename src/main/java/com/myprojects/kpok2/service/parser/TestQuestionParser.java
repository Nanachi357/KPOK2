package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Component responsible for parsing test questions from HTML
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestQuestionParser {

    private static final String LEGACY_UKRAINIAN_CORRECT_ANSWER_PREFIX = "Правильна відповідь: ";

    private final TestNormalizer normalizer;

    /**
     * Parse test questions from the current page using WebDriver
     */
    public List<ParsedTestQuestionDto> parsePage(WebDriver driver, String url) {
        return parsePage(driver, url, null);
    }

    public List<ParsedTestQuestionDto> parsePage(
            WebDriver driver, String url, QuizTarget quizTarget) {
        try {
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);
            Long quizId = quizTarget == null ? null : quizTarget.courseModuleId();
            String sourceUrl = quizTarget == null ? url : quizTarget.viewUrl();
            return parseQuestions(doc, sourceUrl, quizId);
        } catch (Exception e) {
            log.error("Error parsing page {}: {}", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses all questions from the document
     * @param doc JSoup Document containing the HTML of the test page
     * @return List of parsed test questions
     */
    public List<ParsedTestQuestionDto> parseQuestions(Document doc) {
        return parseQuestions(doc, null, null);
    }

    public List<ParsedTestQuestionDto> parseQuestions(
            Document doc, String sourceUrl, Long sourceQuizId) {
        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        Elements questionElements = doc.select(TestParserConstants.QUESTION_SELECTOR);
        String sourceLanguage = documentLanguage(doc);
        
        log.info("Found {} question elements", questionElements.size());
        
        for (Element questionElement : questionElements) {
            try {
                ParsedTestQuestionDto question = parseQuestion(
                        questionElement, sourceUrl, sourceQuizId, sourceLanguage);
                if (question != null) {
                    questions.add(question);
                }
            } catch (Exception e) {
                log.warn("Error parsing question: {}", e.getMessage());
            }
        }
        
        return questions;
    }
    
    /**
     * Parses an individual question
     * @param questionElement JSoup Element containing the question HTML
     * @return Parsed test question
     */
    private ParsedTestQuestionDto parseQuestion(
            Element questionElement,
            String sourceUrl,
            Long sourceQuizId,
            String sourceLanguage) {
        try {
            Element questionTextElement = questionElement.selectFirst("div.qtext");
            if (questionTextElement == null) {
                log.warn("Question text not found in container");
                return null;
            }
            String questionText = questionTextElement.text().trim();

            QuestionType questionType = detectQuestionType(questionElement);
            List<AnswerOption> options = extractAnswerOptions(questionElement);
            List<String> answers = options.stream().map(AnswerOption::text).toList();
            List<String> legacyAnswers = questionElement
                    .select("div.answer div.r0, div.answer div.r1")
                    .stream()
                    .map(Element::text)
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .toList();

            Element correctAnswerElement = questionElement.selectFirst("div.rightanswer");
            String feedbackText = correctAnswerElement == null
                    ? ""
                    : correctAnswerElement.text().trim();
            List<String> correctAnswers = extractCorrectAnswers(
                    options, feedbackText, questionType);

            if (correctAnswers.isEmpty()
                    && !feedbackText.isBlank()
                    && questionType == QuestionType.UNKNOWN) {
                // Unknown Moodle question types may not expose selectable options.
                // Preserve their feedback instead of discarding the question.
                correctAnswers = List.of(feedbackText);
            }
            if (correctAnswers.isEmpty()) {
                log.warn("Correct answer not found in container");
                return null;
            }

            List<String> normalizedAnswers = answers.stream()
                    .map(normalizer::normalizeAnswer)
                    .toList();
            List<String> normalizedCorrectAnswers = correctAnswers.stream()
                    .map(normalizer::normalizeAnswer)
                    .toList();
            String correctAnswer = String.join(" | ", correctAnswers);
            String normalizedCorrectAnswer = String.join(" | ", normalizedCorrectAnswers);
            String legacyCorrectAnswer = feedbackText
                    .replace(LEGACY_UKRAINIAN_CORRECT_ANSWER_PREFIX, "")
                    .trim();
            if (legacyCorrectAnswer.isBlank()) {
                legacyCorrectAnswer = correctAnswer;
            }

            return ParsedTestQuestionDto.builder()
                    .questionText(questionText)
                    .normalizedText(normalizer.normalizeText(questionText))
                    .answers(answers)
                    .legacyAnswers(legacyAnswers)
                    .normalizedAnswers(normalizedAnswers)
                    .correctAnswers(correctAnswers)
                    .normalizedCorrectAnswers(normalizedCorrectAnswers)
                    .correctAnswer(correctAnswer)
                    .normalizedCorrectAnswer(normalizedCorrectAnswer)
                    .legacyNormalizedText(normalizer.normalizeLegacyText(questionText))
                    .legacyNormalizedCorrectAnswer(normalizer.normalizeLegacyText(legacyCorrectAnswer))
                    .normalizationVersion(TestNormalizer.CURRENT_VERSION)
                    .questionType(questionType)
                    .sourceQuizId(sourceQuizId)
                    .sourceUrl(sourceUrl)
                    .sourceLanguage(sourceLanguage)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing question container: {}", e.getMessage());
            return null;
        }
    }

    private List<AnswerOption> extractAnswerOptions(Element questionElement) {
        List<AnswerOption> options = new ArrayList<>();
        for (Element option : questionElement.select("div.answer div.r0, div.answer div.r1")) {
            Element clean = option.clone();
            clean.select("input, i.icon, .specificfeedback, .feedback, .validationerror").remove();
            String text = clean.text().trim();
            if (!text.isEmpty()) {
                boolean structurallyCorrect = option.hasClass("correct")
                        || !option.select(".correct, i.text-success, i.fa-check").isEmpty();
                options.add(new AnswerOption(text, structurallyCorrect));
            }
        }
        return options;
    }

    private List<String> extractCorrectAnswers(
            List<AnswerOption> options, String feedbackText, QuestionType questionType) {
        List<String> structuralMatches = options.stream()
                .filter(AnswerOption::structurallyCorrect)
                .map(AnswerOption::text)
                .toList();
        if (!structuralMatches.isEmpty()) {
            return structuralMatches;
        }
        if (feedbackText.isBlank()) {
            return List.of();
        }

        String normalizedFeedback = normalizer.normalizeText(feedbackText);
        List<AnswerOption> longestFirst = options.stream()
                .sorted(Comparator.comparingInt(
                        (AnswerOption option) -> normalizer.normalizeAnswer(option.text()).length())
                        .reversed())
                .toList();

        if (questionType != QuestionType.MULTIPLE_CHOICE) {
            return longestFirst.stream()
                    .filter(option -> endsWithPhrase(
                            normalizedFeedback, normalizer.normalizeAnswer(option.text())))
                    .findFirst()
                    .or(() -> longestFirst.stream().filter(option -> containsPhrase(
                            normalizedFeedback, normalizer.normalizeAnswer(option.text()))).findFirst())
                    .or(() -> longestFirst.stream().filter(option -> containsCompactAnswer(
                            normalizedFeedback, normalizer.normalizeAnswer(option.text()))).findFirst())
                    .map(option -> List.of(option.text()))
                    .orElseGet(List::of);
        }

        String remainingFeedback = " " + normalizedFeedback + " ";
        Set<String> matched = new LinkedHashSet<>();
        for (AnswerOption option : longestFirst) {
            String normalizedOption = normalizer.normalizeAnswer(option.text());
            String phrase = " " + normalizedOption + " ";
            if (!normalizedOption.isBlank() && remainingFeedback.contains(phrase)) {
                matched.add(option.text());
                remainingFeedback = remainingFeedback.replace(phrase, " ");
            }
        }

        if (matched.isEmpty()) {
            for (AnswerOption option : longestFirst) {
                String normalizedOption = normalizer.normalizeAnswer(option.text());
                if (containsCompactAnswer(remainingFeedback, normalizedOption)) {
                    matched.add(option.text());
                    remainingFeedback = remainingFeedback.replace(normalizedOption, " ");
                }
            }
        }

        return options.stream()
                .map(AnswerOption::text)
                .filter(matched::contains)
                .toList();
    }

    private QuestionType detectQuestionType(Element questionElement) {
        if (questionElement.hasClass("truefalse")) {
            return QuestionType.TRUE_FALSE;
        }
        if (questionElement.hasClass("multichoice")) {
            return questionElement.select("div.answer input[type=checkbox]").isEmpty()
                    ? QuestionType.SINGLE_CHOICE
                    : QuestionType.MULTIPLE_CHOICE;
        }
        return QuestionType.UNKNOWN;
    }

    private String documentLanguage(Document document) {
        Element html = document.selectFirst("html[lang]");
        if (html == null || html.attr("lang").isBlank()) {
            return null;
        }
        return html.attr("lang").split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
    }

    private boolean containsPhrase(String text, String phrase) {
        return !phrase.isBlank() && (" " + text + " ").contains(" " + phrase + " ");
    }

    private boolean endsWithPhrase(String text, String phrase) {
        return !phrase.isBlank() && (text.equals(phrase) || text.endsWith(" " + phrase));
    }

    private boolean containsCompactAnswer(String text, String answer) {
        if (answer.isBlank() || !text.contains(answer)) {
            return false;
        }

        int codePointCount = answer.codePointCount(0, answer.length());
        if (codePointCount >= 2) {
            return true;
        }

        int codePoint = answer.codePointAt(0);
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private record AnswerOption(String text, boolean structurallyCorrect) {
    }
    
    /**
     * Prints a summary of the parsed questions
     * @param questions List of parsed questions
     */
    public void printQuestionsSummary(List<ParsedTestQuestionDto> questions) {
        if (questions.isEmpty()) {
            log.info("No questions found on the page");
            return;
        }
        
        log.info("=== Questions Summary ===");
        log.info("Total questions found: {}", questions.size());
        
        // Print details for the first few questions
        int questionsToShow = Math.min(5, questions.size());
        for (int i = 0; i < questionsToShow; i++) {
            ParsedTestQuestionDto question = questions.get(i);
            log.info("Question {}: {}", i + 1, question.getQuestionText());
            log.info("  Answer options: {}", question.getAnswers().size());
            for (int j = 0; j < question.getAnswers().size(); j++) {
                log.info("    {}. {}", (char)('A' + j), question.getAnswers().get(j));
            }
            log.info("  Correct answer: {}", question.getCorrectAnswer());
            log.info("---");
        }
        
        if (questions.size() > questionsToShow) {
            log.info("... and {} more questions", questions.size() - questionsToShow);
        }
    }
}

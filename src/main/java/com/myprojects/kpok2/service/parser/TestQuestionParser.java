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
    private final RichQuestionContentExtractor richContentExtractor =
            new RichQuestionContentExtractor();

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
            Document doc = Jsoup.parse(pageSource, url);
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
        return parseQuestions(doc, sourceUrl, sourceQuizId, null);
    }

    public List<ParsedTestQuestionDto> parseQuestions(
            Document doc,
            String sourceUrl,
            Long sourceQuizId,
            String languageHint) {
        long startedNanos = System.nanoTime();
        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        Elements pageElements = doc.select(TestParserConstants.QUESTION_SELECTOR);
        String documentLanguage = documentLanguage(doc);
        String contentBaseUrl = doc.baseUri().isBlank() ? sourceUrl : doc.baseUri();
        Element contextElement = null;

        int questionCount = 0;
        int contextCount = 0;
        for (Element pageElement : pageElements) {
            if (isContextBlock(pageElement)) {
                contextCount++;
            } else {
                questionCount++;
            }
        }
        log.info("Found {} question containers and {} context blocks",
                questionCount, contextCount);

        for (Element questionElement : pageElements) {
            if (isContextBlock(questionElement)) {
                contextElement = questionElement;
                continue;
            }
            try {
                ParsedTestQuestionDto question = parseQuestion(
                        questionElement,
                        sourceUrl,
                        sourceQuizId,
                        contextElement,
                        documentLanguage,
                        languageHint,
                        contentBaseUrl);
                if (question != null) {
                    questions.add(question);
                }
            } catch (Exception e) {
                log.warn("Error parsing question: {}", e.getMessage());
            }
        }
        
        log.info("Parsed {} questions in {} ms",
                questions.size(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos));
        return questions;
    }

    /**
     * Counts only answerable Moodle question containers. Description and
     * information blocks provide context but are not questions themselves.
     */
    public int countQuestionContainers(Document document) {
        return (int) document.select(TestParserConstants.QUESTION_SELECTOR).stream()
                .filter(element -> !isContextBlock(element))
                .count();
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
            Element contextElement,
            String documentLanguage,
            String languageHint,
            String contentBaseUrl) {
        try {
            Element questionTextElement = questionElement.selectFirst("div.qtext");
            if (questionTextElement == null) {
                log.warn("Question text not found in container");
                return null;
            }
            String questionText = textWithImageAlternatives(
                    questionTextElement, "[Image question]");
            String contextText = extractContextText(contextElement);

            QuestionType questionType = detectQuestionType(questionElement);
            List<Element> answerElements = questionElement
                    .select("div.answer div.r0, div.answer div.r1");
            RichQuestionContentExtractor.RichQuestionContent richContent =
                    richContentExtractor.extract(
                            contextElement,
                            questionTextElement,
                            answerElements,
                            contentBaseUrl);
            List<AnswerOption> options = extractAnswerOptions(answerElements);
            List<String> answers = options.stream().map(AnswerOption::text).toList();
            List<String> legacyAnswers = answerElements.stream()
                    .map(Element::text)
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .toList();
            String sourceLanguage = contentLanguage(
                    String.join(" ", List.of(
                            contextText == null ? "" : contextText,
                            questionText,
                            String.join(" ", answers))),
                    documentLanguage,
                    languageHint);

            Element correctAnswerElement = questionElement.selectFirst("div.rightanswer");
            String feedbackText = correctAnswerElement == null
                    ? ""
                    : correctAnswerElement.text().trim();
            List<String> correctAnswers = extractCorrectAnswers(
                    options, feedbackText, questionType);
            List<Integer> correctAnswerIndices = correctAnswerIndices(
                    options, correctAnswers);

            if (correctAnswers.isEmpty()
                    && !feedbackText.isBlank()
                    && questionType == QuestionType.UNKNOWN) {
                // Unknown Moodle question types may not expose selectable options.
                // Preserve their feedback instead of discarding the question.
                correctAnswers = List.of(feedbackText);
                correctAnswerIndices = List.of();
            }
            if (correctAnswers.isEmpty()) {
                log.warn("Correct answer not found in container");
                return null;
            }

            List<String> normalizedAnswers = options.stream()
                    .map(AnswerOption::normalizedText)
                    .toList();
            Set<String> correctAnswerSet = new LinkedHashSet<>(correctAnswers);
            List<String> normalizedCorrectAnswers = options.stream()
                    .filter(option -> correctAnswerSet.contains(option.text()))
                    .map(AnswerOption::normalizedText)
                    .toList();
            if (normalizedCorrectAnswers.size() != correctAnswers.size()) {
                normalizedCorrectAnswers = correctAnswers.stream()
                        .map(normalizer::normalizeAnswer)
                        .toList();
            }
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
                    .contextText(contextText)
                    .normalizedContext(normalizer.normalizeText(contextText))
                    .contextHtml(richContent.contextHtml())
                    .questionHtml(richContent.questionHtml())
                    .answers(answers)
                    .answerHtml(richContent.answerHtml())
                    .assets(richContent.assets())
                    .legacyAnswers(legacyAnswers)
                    .normalizedAnswers(normalizedAnswers)
                    .correctAnswers(correctAnswers)
                    .correctAnswerIndices(correctAnswerIndices)
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

    private List<AnswerOption> extractAnswerOptions(List<Element> answerElements) {
        List<AnswerOption> options = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < answerElements.size(); sourceIndex++) {
            Element option = answerElements.get(sourceIndex);
            Element clean = option.clone();
            clean.select("input, i.icon, .specificfeedback, .feedback, .validationerror").remove();
            String text = textWithImageAlternatives(
                    clean, "[Image option " + (sourceIndex + 1) + "]");
            if (!text.isEmpty()) {
                boolean structurallyCorrect = option.hasClass("correct")
                        || !option.select(".correct, i.text-success, i.fa-check").isEmpty();
                options.add(new AnswerOption(
                        text,
                        normalizer.normalizeAnswer(text),
                        structurallyCorrect,
                        sourceIndex));
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
                        (AnswerOption option) -> option.normalizedText().length())
                        .reversed())
                .toList();

        if (questionType != QuestionType.MULTIPLE_CHOICE) {
            return longestFirst.stream()
                    .filter(option -> endsWithPhrase(
                            normalizedFeedback, option.normalizedText()))
                    .findFirst()
                    .or(() -> longestFirst.stream().filter(option -> containsPhrase(
                            normalizedFeedback, option.normalizedText())).findFirst())
                    .or(() -> longestFirst.stream().filter(option -> containsCompactAnswer(
                            normalizedFeedback, option.normalizedText())).findFirst())
                    .map(option -> List.of(option.text()))
                    .orElseGet(List::of);
        }

        String remainingFeedback = " " + normalizedFeedback + " ";
        Set<String> matched = new LinkedHashSet<>();
        for (AnswerOption option : longestFirst) {
            String normalizedOption = option.normalizedText();
            String phrase = " " + normalizedOption + " ";
            if (!normalizedOption.isBlank() && remainingFeedback.contains(phrase)) {
                matched.add(option.text());
                remainingFeedback = remainingFeedback.replace(phrase, " ");
            }
        }

        if (matched.isEmpty()) {
            for (AnswerOption option : longestFirst) {
                String normalizedOption = option.normalizedText();
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

    private List<Integer> correctAnswerIndices(
            List<AnswerOption> options, List<String> correctAnswers) {
        List<Integer> structuralIndices = options.stream()
                .filter(AnswerOption::structurallyCorrect)
                .map(AnswerOption::sourceIndex)
                .toList();
        if (!structuralIndices.isEmpty()) {
            return structuralIndices;
        }

        List<Integer> indices = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (String correctAnswer : correctAnswers) {
            for (AnswerOption option : options) {
                if (!used.contains(option.sourceIndex())
                        && option.text().equals(correctAnswer)) {
                    indices.add(option.sourceIndex());
                    used.add(option.sourceIndex());
                    break;
                }
            }
        }
        return List.copyOf(indices);
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

    private boolean isContextBlock(Element element) {
        return element.is(TestParserConstants.CONTEXT_SELECTOR);
    }

    private String extractContextText(Element element) {
        if (element == null) {
            return null;
        }
        Element questionText = element.selectFirst(TestParserConstants.QUESTION_TEXT_SELECTOR);
        String text = textWithImageAlternatives(
                questionText == null ? element : questionText,
                "[Image context]");
        return text.isBlank() ? null : text;
    }

    private String textWithImageAlternatives(Element element, String fallback) {
        StringBuilder text = new StringBuilder(element.text().trim());
        element.select("img[alt]").stream()
                .map(image -> image.attr("alt").trim())
                .filter(alt -> !alt.isBlank())
                .distinct()
                .filter(alt -> !text.toString().contains(alt))
                .forEach(alt -> {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(alt);
                });
        if (text.isEmpty() && !element.select("img, svg").isEmpty()) {
            return fallback;
        }
        return text.toString().trim();
    }

    private String contentLanguage(
            String content, String documentLanguage, String languageHint) {
        int latin = 0;
        int cyrillic = 0;
        int han = 0;
        int hiraganaOrKatakana = 0;
        int hangul = 0;
        int greek = 0;
        int arabic = 0;
        int hebrew = 0;
        int devanagari = 0;

        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);
            switch (Character.UnicodeScript.of(codePoint)) {
                case LATIN -> latin++;
                case CYRILLIC -> cyrillic++;
                case HAN -> han++;
                case HIRAGANA, KATAKANA -> hiraganaOrKatakana++;
                case HANGUL -> hangul++;
                case GREEK -> greek++;
                case ARABIC -> arabic++;
                case HEBREW -> hebrew++;
                case DEVANAGARI -> devanagari++;
                default -> {
                    // Punctuation, digits and unsupported scripts carry no
                    // useful language signal here.
                }
            }
        }

        if (hiraganaOrKatakana > 0) {
            return "ja";
        }
        if (hangul > 0) {
            return "ko";
        }
        if (han > Math.max(latin, cyrillic)) {
            return "zh";
        }
        if (cyrillic > latin) {
            if (isCyrillicLanguage(languageHint)) {
                return normalizedLanguage(languageHint);
            }
            if (isCyrillicLanguage(documentLanguage)) {
                return normalizedLanguage(documentLanguage);
            }
            return containsUkrainianLetters(content) ? "uk" : "und";
        }
        if (greek > 0) {
            return "el";
        }
        if (arabic > 0) {
            return "ar";
        }
        if (hebrew > 0) {
            return "he";
        }
        if (devanagari > 0) {
            return "hi";
        }
        if (latin > 0) {
            String hint = normalizedLanguage(languageHint);
            return hint != null && !isCyrillicLanguage(hint) ? hint : "en";
        }

        String hint = normalizedLanguage(languageHint);
        return hint != null ? hint : normalizedLanguage(documentLanguage);
    }

    private boolean containsUkrainianLetters(String content) {
        return content.toLowerCase(Locale.ROOT).matches(".*[іїєґ].*");
    }

    private boolean isCyrillicLanguage(String language) {
        String normalized = normalizedLanguage(language);
        return normalized != null
                && Set.of("uk", "ru", "be", "bg", "mk", "sr").contains(normalized);
    }

    private String normalizedLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim().split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
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

    private record AnswerOption(
            String text,
            String normalizedText,
            boolean structurallyCorrect,
            int sourceIndex) {
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

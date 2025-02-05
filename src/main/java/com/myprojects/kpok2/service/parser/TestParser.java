package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.Answer;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestParser {
    private final WebDriver webDriver;
    private final TestQuestionService testQuestionService;

    public void parseTest(String url) {
        try {
            log.info("Starting to parse test from URL: {}", url);
            webDriver.get(url);

            List<WebElement> questionElements = webDriver.findElements(By.cssSelector(TestParserConstants.QUESTION_SELECTOR));

            for (WebElement questionElement : questionElements) {
                TestQuestion testQuestion = parseQuestion(questionElement);
                testQuestionService.saveQuestion(testQuestion);
            }

            log.info("Finished parsing test");
        } catch (Exception e) {
            log.error("Error parsing test: ", e);
            throw new RuntimeException("Error parsing test: " + e.getMessage(), e);
        } finally {
            webDriver.quit();
        }
    }

    private TestQuestion parseQuestion(WebElement questionElement) {
        TestQuestion question = new TestQuestion();
        question.setQuestion(questionElement.getText());

        WebElement answersContainer = questionElement.findElement(By.xpath("./following-sibling::fieldset"));
        List<Answer> answers = parseAnswers(answersContainer);
        question.setAnswers(answers);

        WebElement correctAnswerElement = questionElement.findElement(
                By.xpath("./following-sibling::div[contains(@class, 'rightanswer')]")
        );
        question.setCorrectAnswer(correctAnswerElement.getText().replace("Правильна відповідь:", "").trim());

        return question;
    }

    private List<Answer> parseAnswers(WebElement answersContainer) {
        List<Answer> answers = new ArrayList<>();
        List<WebElement> answerElements = answersContainer.findElements(
                By.cssSelector(TestParserConstants.ANSWERS_CONTAINER_SELECTOR)
        );

        for (WebElement answerElement : answerElements) {
            Answer answer = new Answer();
            answer.setLetter(answerElement.findElement(By.cssSelector("span.answernumber")).getText().replace(".", "").trim());
            answer.setText(answerElement.findElement(By.cssSelector(TestParserConstants.ANSWER_TEXT_SELECTOR)).getText());
            answers.add(answer);
        }

        return answers;
    }
}
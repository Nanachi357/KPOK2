package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.TestCenterConfig;
import com.myprojects.kpok2.model.Answer;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestParser {
    private final WebDriver webDriver;
    private final TestQuestionService testQuestionService;
    private final TestCenterConfig testCenterConfig;


    private void login() {
        try {
            log.info("Starting login process");
            webDriver.get(TestParserConstants.LOGIN_URL);

            WebElement usernameInput = webDriver.findElement(By.cssSelector(TestParserConstants.USERNAME_SELECTOR));
            WebElement passwordInput = webDriver.findElement(By.cssSelector(TestParserConstants.PASSWORD_SELECTOR));
            WebElement loginButton = webDriver.findElement(By.cssSelector(TestParserConstants.LOGIN_BUTTON_SELECTOR));

            usernameInput.sendKeys(testCenterConfig.getUsername());
            passwordInput.sendKeys(testCenterConfig.getPassword());
            loginButton.click();

            // Wait for login to complete
            new WebDriverWait(webDriver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.urlContains("test.testcentr.org.ua"));

            log.info("Successfully logged in");
        } catch (Exception e) {
            log.error("Failed to login: ", e);
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

    public void parseTest(String url) {
        try {
            log.info("Starting to parse test from URL: {}", url);
            login();
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
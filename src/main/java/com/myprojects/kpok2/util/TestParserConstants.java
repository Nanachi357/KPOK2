package com.myprojects.kpok2.util;

public class TestParserConstants {
    // Selectors for test questions and answers
    public static final String QUESTION_SELECTOR = "div.qtext";
    public static final String ANSWERS_CONTAINER_SELECTOR = "div.answer";
    public static final String ANSWER_LETTER_SELECTOR = "span.answernumber";
    public static final String ANSWER_TEXT_SELECTOR = "div.flex-fill.ml-1";
    public static final String CORRECT_ANSWER_SELECTOR = "div.rightanswer";
    public static final String QUESTION_CONTAINER_SELECTOR = "div.que";

    // Navigation selectors
    public static final String NEXT_PAGE_SELECTOR = "a.mod_quiz-next-nav";
    public static final String FINISH_ATTEMPT_SELECTOR = "input[name='finishattempt']";

    // Login page selectors
    public static final String LOGIN_URL = "https://test.testcentr.org.ua/login/index.php";
    public static final String USERNAME_SELECTOR = "input#username";
    public static final String PASSWORD_SELECTOR = "input#password";
    public static final String LOGIN_BUTTON_SELECTOR = "button#loginbtn";

    // Error message selectors
    public static final String ERROR_MESSAGE_SELECTOR = "div.alert-danger";

    // Wait timeouts (in seconds)
    public static final int DEFAULT_TIMEOUT = 10;
    public static final int PAGE_LOAD_TIMEOUT = 30;
    public static final int LOGIN_TIMEOUT = 15;

    // Regular expressions for text normalization
    public static final String WHITESPACE_REGEX = "\\s+";
    public static final String SPECIAL_CHARS_REGEX = "[^\\p{L}\\p{N}\\s]";

    private TestParserConstants() {
        // Private constructor to prevent instantiation
    }
}
package com.myprojects.kpok2.util;

public class TestParserConstants {
    // Selectors for test questions and answers
    public static final String QUESTION_SELECTOR = "div.qtext";
    public static final String ANSWERS_CONTAINER_SELECTOR = "div.answer";
    public static final String ANSWER_TEXT_SELECTOR = "div.flex-fill.ml-1";
    public static final String CORRECT_ANSWER_SELECTOR = "div.rightanswer";

    // Login page selectors
    public static final String LOGIN_URL = "https://test.testcentr.org.ua/login/index.php";
    public static final String USERNAME_SELECTOR = "input#username";
    public static final String PASSWORD_SELECTOR = "input#password";
    public static final String LOGIN_BUTTON_SELECTOR = "button#loginbtn";

    // Text constants
    public static final String CORRECT_ANSWER_PREFIX = "Правильна відповідь: ";

    private TestParserConstants() {
        // Private constructor to prevent instantiation
    }
}
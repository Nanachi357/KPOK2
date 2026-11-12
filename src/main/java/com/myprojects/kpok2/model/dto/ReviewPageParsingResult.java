package com.myprojects.kpok2.model.dto;

/**
 * Result of processing one Moodle review page.
 */
public record ReviewPageParsingResult(
        String url,
        int discoveredQuestions,
        int parsedQuestions,
        int newQuestions,
        String errorMessage) {

    public ReviewPageParsingResult {
        discoveredQuestions = Math.max(0, discoveredQuestions);
        parsedQuestions = Math.max(0, parsedQuestions);
        newQuestions = Math.max(0, newQuestions);
    }

    public static ReviewPageParsingResult failure(String url, String errorMessage) {
        return new ReviewPageParsingResult(url, 0, 0, 0, errorMessage);
    }

    public boolean isComplete() {
        return errorMessage == null
                && discoveredQuestions > 0
                && discoveredQuestions == parsedQuestions;
    }

    public int duplicateQuestions() {
        return Math.max(0, parsedQuestions - newQuestions);
    }

    public int rejectedQuestions() {
        return Math.max(0, discoveredQuestions - parsedQuestions);
    }
}

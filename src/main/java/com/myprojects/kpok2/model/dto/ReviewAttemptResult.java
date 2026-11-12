package com.myprojects.kpok2.model.dto;

import java.util.List;

/**
 * Completeness summary for all review pages of one completed attempt.
 */
public record ReviewAttemptResult(List<ReviewPageParsingResult> pages) {

    public ReviewAttemptResult {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public boolean isComplete() {
        return !pages.isEmpty() && pages.stream().allMatch(ReviewPageParsingResult::isComplete);
    }

    public int successfulPages() {
        return (int) pages.stream().filter(ReviewPageParsingResult::isComplete).count();
    }

    public int failedPages() {
        return pages.size() - successfulPages();
    }

    public int discoveredQuestions() {
        return pages.stream().mapToInt(ReviewPageParsingResult::discoveredQuestions).sum();
    }

    public int parsedQuestions() {
        return pages.stream().mapToInt(ReviewPageParsingResult::parsedQuestions).sum();
    }

    public int newQuestions() {
        return pages.stream().mapToInt(ReviewPageParsingResult::newQuestions).sum();
    }

    public int duplicateQuestions() {
        return pages.stream().mapToInt(ReviewPageParsingResult::duplicateQuestions).sum();
    }

    public int rejectedQuestions() {
        return pages.stream().mapToInt(ReviewPageParsingResult::rejectedQuestions).sum();
    }
}

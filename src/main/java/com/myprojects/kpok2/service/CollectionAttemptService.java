package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionAttemptService {

    private final CollectionAttemptRepository repository;

    @Transactional
    public void recordAttempt(
            NavigationSession session,
            List<String> reviewUrls,
            ReviewAttemptResult result) {
        if (session == null || session.getQuizTarget() == null
                || session.getAttemptId() == null || session.getAttemptId().isBlank()
                || result == null) {
            return;
        }

        QuizTarget target = session.getQuizTarget();
        String attemptId = session.getAttemptId();
        String reviewUrl = firstReviewUrl(reviewUrls, target, attemptId);
        LocalDateTime collectedAt = LocalDateTime.now();
        String error = errorSummary(result);

        CollectionAttempt attempt = repository
                .findByQuizIdAndSourceAttemptId(target.courseModuleId(), attemptId)
                .orElseGet(() -> newAttempt(
                        target, attemptId, reviewUrl, collectedAt, result, error));

        if (attempt.getId() != null) {
            updateAttempt(attempt, target, reviewUrl, collectedAt, result, error);
        }
        repository.save(attempt);
    }

    private static CollectionAttempt newAttempt(
            QuizTarget target,
            String attemptId,
            String reviewUrl,
            LocalDateTime collectedAt,
            ReviewAttemptResult result,
            String error) {
        return CollectionAttempt.builder()
                .quizId(target.courseModuleId())
                .sourceAttemptId(attemptId)
                .quizUrl(target.viewUrl())
                .reviewUrl(reviewUrl)
                .firstCollectedAt(collectedAt)
                .lastCollectedAt(collectedAt)
                .collectionRuns(1)
                .pageCount(result.pages().size())
                .successfulPageCount(result.successfulPages())
                .discoveredQuestions(result.discoveredQuestions())
                .parsedQuestions(result.parsedQuestions())
                .initialNewQuestions(result.newQuestions())
                .latestNewQuestions(result.newQuestions())
                .latestDuplicateQuestions(result.duplicateQuestions())
                .rejectedQuestions(result.rejectedQuestions())
                .complete(result.isComplete())
                .lastError(error)
                .build();
    }

    private static void updateAttempt(
            CollectionAttempt attempt,
            QuizTarget target,
            String reviewUrl,
            LocalDateTime collectedAt,
            ReviewAttemptResult result,
            String error) {
        attempt.setQuizUrl(target.viewUrl());
        attempt.setReviewUrl(reviewUrl);
        attempt.setLastCollectedAt(collectedAt);
        attempt.setCollectionRuns(attempt.getCollectionRuns() + 1);
        attempt.setPageCount(result.pages().size());
        attempt.setSuccessfulPageCount(result.successfulPages());
        attempt.setDiscoveredQuestions(result.discoveredQuestions());
        attempt.setParsedQuestions(result.parsedQuestions());
        attempt.setLatestNewQuestions(result.newQuestions());
        attempt.setLatestDuplicateQuestions(result.duplicateQuestions());
        attempt.setRejectedQuestions(result.rejectedQuestions());
        attempt.setComplete(result.isComplete());
        attempt.setLastError(error);
    }

    private static String firstReviewUrl(
            List<String> reviewUrls, QuizTarget target, String attemptId) {
        if (reviewUrls != null) {
            for (String url : reviewUrls) {
                if (url != null && !url.isBlank()) {
                    return url.trim();
                }
            }
        }
        return target.reviewUrl(attemptId);
    }

    private static String errorSummary(ReviewAttemptResult result) {
        String errors = result.pages().stream()
                .map(ReviewPageParsingResult::errorMessage)
                .filter(Objects::nonNull)
                .filter(message -> !message.isBlank())
                .distinct()
                .collect(Collectors.joining(" | "));
        if (!errors.isBlank()) {
            return errors;
        }
        return result.pages().isEmpty() ? "No review pages were resolved" : null;
    }
}

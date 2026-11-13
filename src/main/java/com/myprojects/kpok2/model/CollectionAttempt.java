package com.myprojects.kpok2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persistent collection summary for one Moodle quiz attempt. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "collection_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_collection_attempt",
                columnNames = {"quiz_id", "source_attempt_id"}),
        indexes = @Index(name = "idx_collection_attempts_quiz", columnList = "quiz_id"))
public class CollectionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "source_attempt_id", nullable = false, length = 64)
    private String sourceAttemptId;

    @Column(name = "quiz_url", nullable = false, columnDefinition = "TEXT")
    private String quizUrl;

    @Column(name = "review_url", nullable = false, columnDefinition = "TEXT")
    private String reviewUrl;

    @Column(name = "first_collected_at", nullable = false)
    private LocalDateTime firstCollectedAt;

    @Column(name = "last_collected_at", nullable = false)
    private LocalDateTime lastCollectedAt;

    @Column(name = "collection_runs", nullable = false)
    private int collectionRuns;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "successful_page_count", nullable = false)
    private int successfulPageCount;

    @Column(name = "discovered_questions", nullable = false)
    private int discoveredQuestions;

    @Column(name = "parsed_questions", nullable = false)
    private int parsedQuestions;

    @Column(name = "initial_new_questions", nullable = false)
    private int initialNewQuestions;

    @Column(name = "latest_new_questions", nullable = false)
    private int latestNewQuestions;

    @Column(name = "latest_duplicate_questions", nullable = false)
    private int latestDuplicateQuestions;

    @Column(name = "rejected_questions", nullable = false)
    private int rejectedQuestions;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}

package com.myprojects.kpok2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Links a revision and answer key to the quiz attempt where they were observed. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "question_observations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_observation",
                columnNames = {
                        "revision_id", "answer_key_id", "quiz_id",
                        "source_attempt_id", "language"
                }),
        indexes = {
                @Index(name = "idx_question_observations_question", columnList = "question_id"),
                @Index(name = "idx_question_observations_quiz", columnList = "quiz_id"),
                @Index(name = "idx_question_observations_attempt", columnList = "source_attempt_id")
        })
public class QuestionObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private TestQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private QuestionRevision revision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_key_id", nullable = false)
    private AnswerKey answerKey;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "quiz_url", nullable = false, columnDefinition = "TEXT")
    private String quizUrl;

    @Column(name = "source_attempt_id", nullable = false, length = 64)
    private String sourceAttemptId;

    @Column(name = "language", nullable = false, length = 32)
    private String language;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "observation_count", nullable = false)
    private int observationCount;
}

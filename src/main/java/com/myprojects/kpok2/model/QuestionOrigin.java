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

/**
 * Records a quiz and language in which a canonical question was observed.
 * A question can therefore belong to several catalog entries without being
 * duplicated in {@code test_questions}.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "question_origins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_origin",
                columnNames = {"question_id", "quiz_id", "language"}),
        indexes = {
                @Index(name = "idx_question_origins_question", columnList = "question_id"),
                @Index(name = "idx_question_origins_quiz", columnList = "quiz_id")
        })
public class QuestionOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private TestQuestion question;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "quiz_url", nullable = false, columnDefinition = "TEXT")
    private String quizUrl;

    @Column(name = "language", nullable = false, length = 32)
    private String language;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}

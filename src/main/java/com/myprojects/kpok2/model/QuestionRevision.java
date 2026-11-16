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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A distinct set of answer options observed for one canonical question. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "question_revisions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_revision",
                columnNames = {"question_id", "revision_hash"}),
        indexes = @Index(name = "idx_question_revisions_question", columnList = "question_id"))
public class QuestionRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private TestQuestion question;

    @Column(name = "revision_hash", nullable = false, length = 64)
    private String revisionHash;

    @Column(name = "context_text", columnDefinition = "TEXT")
    private String contextText;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "possible_answers_json", nullable = false, columnDefinition = "jsonb")
    private String possibleAnswersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_answers_json", nullable = false, columnDefinition = "jsonb")
    private String normalizedAnswersJson;

    @Column(name = "question_type", nullable = false, length = 32)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private QuestionType questionType;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}

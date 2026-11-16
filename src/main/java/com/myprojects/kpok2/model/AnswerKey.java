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

/** One observed correct-answer set for a question revision. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "answer_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_answer_key",
                columnNames = {"revision_id", "key_hash"}),
        indexes = @Index(name = "idx_answer_keys_revision", columnList = "revision_id"))
public class AnswerKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private QuestionRevision revision;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answers_json", nullable = false, columnDefinition = "jsonb")
    private String correctAnswersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_correct_answers_json", nullable = false, columnDefinition = "jsonb")
    private String normalizedCorrectAnswersJson;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}

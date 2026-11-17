package com.myprojects.kpok2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Placement of an image in one question revision. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "question_revision_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_revision_asset_reference",
                columnNames = {"revision_id", "reference_key"}),
        indexes = {
                @Index(
                        name = "idx_question_revision_assets_revision",
                        columnList = "revision_id"),
                @Index(
                        name = "idx_question_revision_assets_asset",
                        columnList = "asset_id")
        })
public class QuestionRevisionAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private QuestionRevision revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private QuestionAsset asset;

    @Column(name = "reference_key", nullable = false, length = 128)
    private String referenceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_location", nullable = false, length = 32)
    private QuestionContentLocation contentLocation;

    @Column(name = "answer_index", nullable = false)
    private int answerIndex;

    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "alt_text", nullable = false, columnDefinition = "TEXT")
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false, length = 32)
    private AssetFetchStatus fetchStatus;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}

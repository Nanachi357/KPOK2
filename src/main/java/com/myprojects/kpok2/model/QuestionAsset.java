package com.myprojects.kpok2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Binary question asset deduplicated globally by its SHA-256 content hash. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "question_assets",
        indexes = @Index(
                name = "uk_question_asset_content_hash",
                columnList = "content_hash",
                unique = true))
public class QuestionAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

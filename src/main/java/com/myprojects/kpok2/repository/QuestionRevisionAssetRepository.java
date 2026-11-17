package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionRevisionAsset;
import com.myprojects.kpok2.model.dto.export.QuestionAssetExportMetadata;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRevisionAssetRepository
        extends JpaRepository<QuestionRevisionAsset, Long> {

    Optional<QuestionRevisionAsset> findByRevisionIdAndReferenceKey(
            Long revisionId, String referenceKey);

    @EntityGraph(attributePaths = "asset")
    List<QuestionRevisionAsset>
            findAllByRevisionIdInOrderByRevisionIdAscContentLocationAscAnswerIndexAscSequenceIndexAsc(
                    Collection<Long> revisionIds);

    @Query("""
            SELECT new com.myprojects.kpok2.model.dto.export.QuestionAssetExportMetadata(
                placement.id,
                placement.revision.id,
                asset.id,
                asset.contentHash,
                asset.mediaType,
                asset.originalFileName,
                asset.contentLength,
                placement.contentLocation,
                placement.answerIndex,
                placement.sequenceIndex,
                placement.sourceUrl,
                placement.altText,
                placement.fetchStatus,
                placement.failureReason)
            FROM QuestionRevisionAsset placement
            LEFT JOIN placement.asset asset
            WHERE placement.revision.id IN :revisionIds
            ORDER BY placement.revision.id,
                placement.contentLocation,
                placement.answerIndex,
                placement.sequenceIndex
            """)
    List<QuestionAssetExportMetadata> findExportMetadataByRevisionIds(
            @Param("revisionIds") Collection<Long> revisionIds);

    @EntityGraph(attributePaths = "asset")
    List<QuestionRevisionAsset>
            findAllByRevisionIdOrderByContentLocationAscAnswerIndexAscSequenceIndexAsc(
                    Long revisionId);
}

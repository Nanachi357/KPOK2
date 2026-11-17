package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionRevisionAsset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    @EntityGraph(attributePaths = "asset")
    List<QuestionRevisionAsset>
            findAllByRevisionIdOrderByContentLocationAscAnswerIndexAscSequenceIndexAsc(
                    Long revisionId);
}

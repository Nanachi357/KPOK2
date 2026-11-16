package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.AnswerKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnswerKeyRepository extends JpaRepository<AnswerKey, Long> {
    Optional<AnswerKey> findByRevisionIdAndKeyHash(Long revisionId, String keyHash);

    long countByRevisionId(Long revisionId);

    List<AnswerKey> findAllByRevisionIdInOrderByRevisionIdAscIdAsc(
            Collection<Long> revisionIds);
}

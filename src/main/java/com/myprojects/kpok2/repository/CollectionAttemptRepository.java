package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.CollectionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface CollectionAttemptRepository extends JpaRepository<CollectionAttempt, Long> {
    List<CollectionAttempt> findAllByOrderByQuizIdAscIdAsc();

    List<CollectionAttempt> findAllByQuizIdInOrderByQuizIdAscIdAsc(
            Collection<Long> quizIds);

    Optional<CollectionAttempt> findByQuizIdAndSourceAttemptId(
            Long quizId, String sourceAttemptId);
}

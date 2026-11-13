package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.CollectionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CollectionAttemptRepository extends JpaRepository<CollectionAttempt, Long> {
    Optional<CollectionAttempt> findByQuizIdAndSourceAttemptId(
            Long quizId, String sourceAttemptId);
}

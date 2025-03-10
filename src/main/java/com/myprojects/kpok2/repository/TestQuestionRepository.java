package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    boolean existsByQuestionHash(String hash);
    Optional<TestQuestion> findByQuestionHash(String hash);

    @Query("SELECT DISTINCT q FROM TestQuestion q " +
            "WHERE LOWER(q.questionText) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "OR LOWER(q.correctAnswer) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<TestQuestion> searchByAnyFragment(@Param("searchText") String searchText);
}
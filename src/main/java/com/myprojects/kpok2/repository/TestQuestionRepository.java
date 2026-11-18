package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    List<TestQuestion> findAllByOrderByIdAsc();

    boolean existsByQuestionHash(String hash);
    boolean existsByQuestionHashIn(Collection<String> hashes);
    Optional<TestQuestion> findByQuestionHash(String hash);
    List<TestQuestion> findAllByQuestionHashIn(Collection<String> hashes);
    Optional<TestQuestion> findFirstByQuestionHashIn(Collection<String> hashes);

    @Query("SELECT DISTINCT q FROM TestQuestion q " +
            "WHERE LOWER(q.questionText) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "OR LOWER(q.contextText) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "OR LOWER(q.correctAnswer) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<TestQuestion> searchByAnyFragment(@Param("searchText") String searchText);

    @Query(value = """
            SELECT DISTINCT q.*
            FROM test_questions q
            LEFT JOIN question_revisions r ON r.question_id = q.id
            LEFT JOIN answer_keys k ON k.revision_id = r.id
            WHERE :searchText = ''
               OR LOWER(COALESCE(q.question_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(q.context_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(q.question_hash, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(r.question_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(r.context_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(r.possible_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(k.correct_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
            ORDER BY q.id DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT q.id)
            FROM test_questions q
            LEFT JOIN question_revisions r ON r.question_id = q.id
            LEFT JOIN answer_keys k ON k.revision_id = r.id
            WHERE :searchText = ''
               OR LOWER(COALESCE(q.question_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(q.context_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(q.question_hash, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(r.question_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(r.context_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(r.possible_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(k.correct_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
            """,
            nativeQuery = true)
    Page<TestQuestion> findLibraryPage(
            @Param("searchText") String searchText,
            Pageable pageable);
}

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
            WHERE (:searchText = ''
               OR STRPOS(LOWER(COALESCE(q.question_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(q.context_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(q.question_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.question_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.context_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.revision_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(k.key_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(CAST(r.possible_answers_json AS TEXT)), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(CAST(k.correct_answers_json AS TEXT)), LOWER(:searchText)) > 0
            )
              AND ((:quizId IS NULL AND :language = '') OR EXISTS (
                    SELECT 1
                    FROM question_origins o
                    WHERE o.question_id = q.id
                      AND (:quizId IS NULL OR o.quiz_id = :quizId)
                      AND (:language = '' OR LOWER(o.language) = :language)
              ))
              AND (:questionType = ''
                   OR q.question_type = :questionType
                   OR EXISTS (
                        SELECT 1 FROM question_revisions tr
                        WHERE tr.question_id = q.id
                          AND tr.question_type = :questionType
                   ))
              AND (:conflictsOnly = FALSE OR EXISTS (
                    SELECT 1
                    FROM question_revisions cr
                    JOIN answer_keys ck ON ck.revision_id = cr.id
                    WHERE cr.question_id = q.id
                    GROUP BY cr.id
                    HAVING COUNT(ck.id) > 1
              ))
            ORDER BY q.id DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT q.id)
            FROM test_questions q
            LEFT JOIN question_revisions r ON r.question_id = q.id
            LEFT JOIN answer_keys k ON k.revision_id = r.id
            WHERE (:searchText = ''
               OR STRPOS(LOWER(COALESCE(q.question_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(q.context_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(q.question_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.question_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.context_text, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(r.revision_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(COALESCE(k.key_hash, '')), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(CAST(r.possible_answers_json AS TEXT)), LOWER(:searchText)) > 0
               OR STRPOS(LOWER(CAST(k.correct_answers_json AS TEXT)), LOWER(:searchText)) > 0
            )
              AND ((:quizId IS NULL AND :language = '') OR EXISTS (
                    SELECT 1
                    FROM question_origins o
                    WHERE o.question_id = q.id
                      AND (:quizId IS NULL OR o.quiz_id = :quizId)
                      AND (:language = '' OR LOWER(o.language) = :language)
              ))
              AND (:questionType = ''
                   OR q.question_type = :questionType
                   OR EXISTS (
                        SELECT 1 FROM question_revisions tr
                        WHERE tr.question_id = q.id
                          AND tr.question_type = :questionType
                   ))
              AND (:conflictsOnly = FALSE OR EXISTS (
                    SELECT 1
                    FROM question_revisions cr
                    JOIN answer_keys ck ON ck.revision_id = cr.id
                    WHERE cr.question_id = q.id
                    GROUP BY cr.id
                    HAVING COUNT(ck.id) > 1
              ))
            """,
            nativeQuery = true)
    Page<TestQuestion> findLibraryPage(
            @Param("searchText") String searchText,
            @Param("quizId") Long quizId,
            @Param("language") String language,
            @Param("questionType") String questionType,
            @Param("conflictsOnly") boolean conflictsOnly,
            Pageable pageable);
}

package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface QuestionRevisionRepository extends JpaRepository<QuestionRevision, Long> {
    Optional<QuestionRevision> findByQuestionIdAndRevisionHash(
            Long questionId, String revisionHash);

    List<QuestionRevision> findAllByOrderByQuestionIdAscIdAsc();

    List<QuestionRevision> findAllByQuestionIdInOrderByQuestionIdAscIdAsc(
            Collection<Long> questionIds);

    @Query(value = """
            SELECT DISTINCT r.*
            FROM question_revisions r
            LEFT JOIN answer_keys k ON k.revision_id = r.id
            WHERE LOWER(r.question_text) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(COALESCE(r.context_text, '')) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(r.possible_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(CAST(k.correct_answers_json AS TEXT)) LIKE LOWER(CONCAT('%', :searchText, '%'))
            ORDER BY r.question_id, r.id
            """, nativeQuery = true)
    List<QuestionRevision> searchRecords(@Param("searchText") String searchText);
}

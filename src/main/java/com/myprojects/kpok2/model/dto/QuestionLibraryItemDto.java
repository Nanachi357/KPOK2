package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.QuestionType;

/** One canonical question row in the searchable library. */
public record QuestionLibraryItemDto(
        Long questionId,
        String contextText,
        String questionText,
        QuestionType questionType,
        int revisionCount,
        int answerKeyCount,
        boolean answerKeyConflict) {
}

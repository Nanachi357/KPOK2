package com.myprojects.kpok2.model.dto;

import java.util.List;

/** Stable API/UI representation of one question-library page. */
public record QuestionLibraryPageDto(
        List<QuestionLibraryItemDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean first,
        boolean last) {

    public QuestionLibraryPageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

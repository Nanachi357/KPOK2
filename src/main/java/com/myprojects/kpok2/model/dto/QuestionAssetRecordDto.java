package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;

/** API/export metadata for one image placement in a question revision. */
public record QuestionAssetRecordDto(
        Long referenceId,
        Long assetId,
        String contentHash,
        String mediaType,
        String originalFileName,
        long contentLength,
        QuestionContentLocation contentLocation,
        int answerIndex,
        int sequenceIndex,
        String sourceUrl,
        String altText,
        AssetFetchStatus fetchStatus,
        String failureReason,
        String contentUrl) {
}

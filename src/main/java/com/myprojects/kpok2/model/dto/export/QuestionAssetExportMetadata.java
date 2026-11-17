package com.myprojects.kpok2.model.dto.export;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;

/** Asset placement metadata selected without loading the binary content column. */
public record QuestionAssetExportMetadata(
        Long referenceId,
        Long revisionId,
        Long assetId,
        String contentHash,
        String mediaType,
        String originalFileName,
        Long contentLength,
        QuestionContentLocation contentLocation,
        int answerIndex,
        int sequenceIndex,
        String sourceUrl,
        String altText,
        AssetFetchStatus fetchStatus,
        String failureReason) {
}

package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;
import lombok.Builder;
import lombok.Data;

/** Mutable internal representation used while an image is fetched and hashed. */
@Data
@Builder
public class ParsedQuestionAssetDto {
    private String referenceId;
    private QuestionContentLocation contentLocation;
    private int answerIndex;
    private int sequenceIndex;
    private String sourceUrl;
    private String altText;
    private String contentHash;
    private String mediaType;
    private String originalFileName;
    private byte[] content;
    private AssetFetchStatus fetchStatus;
    private String failureReason;
}

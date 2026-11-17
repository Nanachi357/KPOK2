package com.myprojects.kpok2.model.dto.importing;

/** Outcome of one atomic, idempotent question import. */
public record QuestionImportResult(
        String schemaVersion,
        int canonicalQuestionsCreated,
        int canonicalQuestionsMerged,
        int revisionsCreated,
        int revisionsMerged,
        int answerKeysCreated,
        int answerKeysMerged,
        int originsCreated,
        int originsMerged,
        int observationsCreated,
        int observationsMerged,
        int attemptsCreated,
        int attemptsMerged,
        int assetsSkipped) {
}

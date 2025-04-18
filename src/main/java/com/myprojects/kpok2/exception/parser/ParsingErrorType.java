package com.myprojects.kpok2.exception.parser;

public enum ParsingErrorType {
    // Access errors
    CONNECTION_ERROR,
    TIMEOUT_ERROR,
    ACCESS_DENIED,

    // Structure errors
    INVALID_PAGE_STRUCTURE,
    MISSING_ELEMENT,
    INVALID_CONTENT,
    NO_QUESTIONS,

    // General errors
    UNEXPECTED_ERROR,
    PROCESSING_ERROR
}
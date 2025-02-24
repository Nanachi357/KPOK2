package com.myprojects.kpok2.exception.parser;

import lombok.Getter;

@Getter
public class ParsingException extends RuntimeException {
    private final ParsingErrorType errorType;

    public ParsingException(ParsingErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ParsingException(ParsingErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

}
package com.myprojects.kpok2.service.parser;

public class TestParsingException extends RuntimeException {
    public TestParsingException(String message) {
        super(message);
    }

    public TestParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
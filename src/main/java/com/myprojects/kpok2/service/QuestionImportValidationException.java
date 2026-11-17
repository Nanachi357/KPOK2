package com.myprojects.kpok2.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** Signals that an import document is readable JSON but is unsafe to import. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class QuestionImportValidationException extends RuntimeException {

    private final List<String> errors;

    public QuestionImportValidationException(List<String> errors) {
        this(errors, null);
    }

    public QuestionImportValidationException(List<String> errors, Throwable cause) {
        super(message(errors), cause);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String message(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Question import validation failed";
        }
        return "Question import validation failed: " + String.join("; ", errors);
    }
}

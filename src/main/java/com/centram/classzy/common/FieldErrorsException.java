package com.centram.classzy.common;

import java.util.Map;

/** Thrown by services that validate several fields at once; rendered as {"message","errors":{field:msg}}. */
public class FieldErrorsException extends RuntimeException {
    private final transient Map<String, String> errors;

    public FieldErrorsException(Map<String, String> errors) {
        super("Please complete the required fields.");
        this.errors = errors;
    }

    public Map<String, String> errors() {
        return errors;
    }
}

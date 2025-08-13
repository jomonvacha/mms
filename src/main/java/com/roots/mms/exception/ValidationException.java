package com.roots.mms.exception;

public class ValidationException extends MemberManagementException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, message));
    }
}

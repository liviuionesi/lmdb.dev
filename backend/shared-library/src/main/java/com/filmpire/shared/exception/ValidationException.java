package com.filmpire.shared.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when validation fails.
 * Results in HTTP 400 status code.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    /**
     * Constructs a new ValidationException with a message
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
        this.fieldErrors = new HashMap<>();
    }

    /**
     * Constructs a new ValidationException with field errors
     *
     * @param message     the detail message
     * @param fieldErrors map of field names to error messages
     */
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
    }

    /**
     * Constructs a new ValidationException with a single field error
     *
     * @param fieldName    the name of the invalid field
     * @param errorMessage the error message for the field
     */
    public ValidationException(String fieldName, String errorMessage) {
        super(String.format("Validation failed for field '%s': %s", fieldName, errorMessage));
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(fieldName, errorMessage);
    }

    /**
     * Gets the field-level validation errors
     *
     * @return map of field names to error messages
     */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * Checks if there are any field errors
     *
     * @return true if field errors exist
     */
    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}
















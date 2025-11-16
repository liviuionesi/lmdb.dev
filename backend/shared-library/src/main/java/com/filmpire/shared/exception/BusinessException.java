package com.filmpire.shared.exception;

/**
 * Exception thrown when a business rule is violated.
 * Results in HTTP 422 status code (Unprocessable Entity).
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    /**
     * Constructs a new BusinessException with a message
     *
     * @param message the detail message
     */
    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
    }

    /**
     * Constructs a new BusinessException with a message and error code
     *
     * @param message   the detail message
     * @param errorCode the error code
     */
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new BusinessException with a message, error code, and cause
     *
     * @param message   the detail message
     * @param errorCode the error code
     * @param cause     the cause
     */
    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Gets the error code
     *
     * @return error code
     */
    public String getErrorCode() {
        return errorCode;
    }
}




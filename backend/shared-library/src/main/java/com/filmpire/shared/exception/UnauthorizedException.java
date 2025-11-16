package com.filmpire.shared.exception;

/**
 * Exception thrown when authentication fails or is required.
 * Results in HTTP 401 status code.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Constructs a new UnauthorizedException with a message
     *
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnauthorizedException with a message and cause
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}




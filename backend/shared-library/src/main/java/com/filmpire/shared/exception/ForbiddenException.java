package com.filmpire.shared.exception;

/**
 * Exception thrown when a user lacks permission to access a resource.
 * Results in HTTP 403 status code.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class ForbiddenException extends RuntimeException {

    /**
     * Constructs a new ForbiddenException with a message
     *
     * @param message the detail message
     */
    public ForbiddenException(String message) {
        super(message);
    }

    /**
     * Constructs a new ForbiddenException with a message and cause
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}




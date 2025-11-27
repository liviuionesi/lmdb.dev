package com.filmpire.shared.exception;

/**
 * Exception thrown when a service is temporarily unavailable.
 * Results in HTTP 503 status code.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    /**
     * Constructs a new ServiceUnavailableException with a message
     *
     * @param message the detail message
     */
    public ServiceUnavailableException(String message) {
        super(message);
        this.serviceName = null;
    }

    /**
     * Constructs a new ServiceUnavailableException for a specific service
     *
     * @param serviceName the name of the unavailable service
     * @param message     the detail message
     */
    public ServiceUnavailableException(String serviceName, String message) {
        super(String.format("%s service is unavailable: %s", serviceName, message));
        this.serviceName = serviceName;
    }

    /**
     * Constructs a new ServiceUnavailableException with a message and cause
     *
     * @param serviceName the name of the unavailable service
     * @param message     the detail message
     * @param cause       the cause
     */
    public ServiceUnavailableException(String serviceName, String message, Throwable cause) {
        super(String.format("%s service is unavailable: %s", serviceName, message), cause);
        this.serviceName = serviceName;
    }

    /**
     * Gets the service name
     *
     * @return service name
     */
    public String getServiceName() {
        return serviceName;
    }
}
















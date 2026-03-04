package com.filmpire.shared.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Results in HTTP 404 status code.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    /**
     * Constructs a new ResourceNotFoundException with a detailed message
     *
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    /**
     * Constructs a new ResourceNotFoundException with resource details
     *
     * @param resourceType the type of resource (e.g., "Movie", "User")
     * @param resourceId   the identifier of the resource
     */
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Constructs a new ResourceNotFoundException with resource details and field name
     *
     * @param resourceType the type of resource
     * @param fieldName    the field name used for lookup
     * @param fieldValue   the field value used for lookup
     */
    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(String.format("%s with %s '%s' not found", resourceType, fieldName, fieldValue));
        this.resourceType = resourceType;
        this.resourceId = String.valueOf(fieldValue);
    }

    /**
     * Gets the resource type
     *
     * @return resource type
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Gets the resource identifier
     *
     * @return resource identifier
     */
    public String getResourceId() {
        return resourceId;
    }
}

















package com.filmpire.shared.constant;

/**
 * Standardized error codes for all microservices.
 * Format: [SERVICE]_[CATEGORY]_[SPECIFIC_ERROR]
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public final class ErrorCodes {

    private ErrorCodes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Generic Errors (1xxx)
    public static final String INTERNAL_SERVER_ERROR = "ERR_1000";
    public static final String VALIDATION_ERROR = "ERR_1001";
    public static final String RESOURCE_NOT_FOUND = "ERR_1002";
    public static final String DUPLICATE_RESOURCE = "ERR_1003";
    public static final String INVALID_REQUEST = "ERR_1004";
    public static final String METHOD_NOT_ALLOWED = "ERR_1005";
    public static final String UNSUPPORTED_MEDIA_TYPE = "ERR_1006";
    public static final String REQUEST_TIMEOUT = "ERR_1007";

    // Authentication Errors (2xxx)
    public static final String UNAUTHORIZED = "ERR_2000";
    public static final String INVALID_CREDENTIALS = "ERR_2001";
    public static final String TOKEN_EXPIRED = "ERR_2002";
    public static final String TOKEN_INVALID = "ERR_2003";
    public static final String TOKEN_MISSING = "ERR_2004";
    public static final String REFRESH_TOKEN_INVALID = "ERR_2005";
    public static final String REFRESH_TOKEN_EXPIRED = "ERR_2006";

    // Authorization Errors (3xxx)
    public static final String FORBIDDEN = "ERR_3000";
    public static final String INSUFFICIENT_PERMISSIONS = "ERR_3001";
    public static final String ACCESS_DENIED = "ERR_3002";
    public static final String ROLE_NOT_FOUND = "ERR_3003";

    // User Service Errors (4xxx)
    public static final String USER_NOT_FOUND = "ERR_4000";
    public static final String USER_ALREADY_EXISTS = "ERR_4001";
    public static final String EMAIL_ALREADY_EXISTS = "ERR_4002";
    public static final String USERNAME_ALREADY_EXISTS = "ERR_4003";
    public static final String INVALID_EMAIL = "ERR_4004";
    public static final String INVALID_PASSWORD = "ERR_4005";
    public static final String PASSWORD_TOO_WEAK = "ERR_4006";
    public static final String USER_INACTIVE = "ERR_4007";
    public static final String USER_BANNED = "ERR_4008";

    // Movie Service Errors (5xxx)
    public static final String MOVIE_NOT_FOUND = "ERR_5000";
    public static final String MOVIE_ALREADY_EXISTS = "ERR_5001";
    public static final String GENRE_NOT_FOUND = "ERR_5002";
    public static final String INVALID_MOVIE_DATA = "ERR_5003";
    public static final String TMDB_API_ERROR = "ERR_5004";
    public static final String RATING_INVALID = "ERR_5005";

    // Actor Service Errors (6xxx)
    public static final String ACTOR_NOT_FOUND = "ERR_6000";
    public static final String ACTOR_ALREADY_EXISTS = "ERR_6001";
    public static final String INVALID_ACTOR_DATA = "ERR_6002";

    // Media Service Errors (7xxx)
    public static final String MEDIA_NOT_FOUND = "ERR_7000";
    public static final String FILE_UPLOAD_FAILED = "ERR_7001";
    public static final String FILE_TOO_LARGE = "ERR_7002";
    public static final String INVALID_FILE_TYPE = "ERR_7003";
    public static final String FILE_PROCESSING_ERROR = "ERR_7004";
    public static final String STORAGE_ERROR = "ERR_7005";
    public static final String FILE_DOWNLOAD_FAILED = "ERR_7006";

    // AI Service Errors (8xxx)
    public static final String AI_SERVICE_ERROR = "ERR_8000";
    public static final String RECOMMENDATION_ERROR = "ERR_8001";
    public static final String VOICE_RECOGNITION_ERROR = "ERR_8002";
    public static final String AI_API_ERROR = "ERR_8003";
    public static final String AI_TIMEOUT = "ERR_8004";

    // External Service Errors (9xxx)
    public static final String SERVICE_UNAVAILABLE = "ERR_9000";
    public static final String EXTERNAL_API_ERROR = "ERR_9001";
    public static final String NETWORK_ERROR = "ERR_9002";
    public static final String CIRCUIT_BREAKER_OPEN = "ERR_9003";
    public static final String RATE_LIMIT_EXCEEDED = "ERR_9004";
    public static final String DATABASE_ERROR = "ERR_9005";
    public static final String CACHE_ERROR = "ERR_9006";

    /**
     * Gets a human-readable description for an error code
     *
     * @param errorCode the error code
     * @return description of the error
     */
    public static String getDescription(String errorCode) {
        return switch (errorCode) {
            case INTERNAL_SERVER_ERROR -> "Internal server error occurred";
            case VALIDATION_ERROR -> "Request validation failed";
            case RESOURCE_NOT_FOUND -> "Requested resource not found";
            case DUPLICATE_RESOURCE -> "Resource already exists";
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Access forbidden";
            case USER_NOT_FOUND -> "User not found";
            case MOVIE_NOT_FOUND -> "Movie not found";
            case ACTOR_NOT_FOUND -> "Actor not found";
            case SERVICE_UNAVAILABLE -> "Service temporarily unavailable";
            default -> "Unknown error occurred";
        };
    }
}












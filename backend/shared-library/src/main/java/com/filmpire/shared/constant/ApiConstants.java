package com.filmpire.shared.constant;

/**
 * API-related constants used across all microservices.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public final class ApiConstants {

    private ApiConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // API Versioning
    public static final String API_VERSION = "/api/v1";
    public static final String API_V1 = "/api/v1";
    public static final String API_V2 = "/api/v2";

    // Common Headers
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String HEADER_API_KEY = "X-API-Key";

    // Content Types
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_XML = "application/xml";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";

    // Pagination
    public static final int DEFAULT_PAGE_NUMBER = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_SIZE = "size";
    public static final String PARAM_SORT = "sort";

    // Common Query Parameters
    public static final String PARAM_SEARCH = "search";
    public static final String PARAM_FILTER = "filter";
    public static final String PARAM_LANG = "lang";
    public static final String PARAM_FIELDS = "fields";

    // Date Formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String DATE_TIME_WITH_ZONE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    // Cache Keys
    public static final String CACHE_PREFIX = "filmpire:";
    public static final String CACHE_MOVIES = CACHE_PREFIX + "movies:";
    public static final String CACHE_USERS = CACHE_PREFIX + "users:";
    public static final String CACHE_ACTORS = CACHE_PREFIX + "actors:";

    // Default TTL (Time To Live) in seconds
    public static final long DEFAULT_CACHE_TTL = 3600; // 1 hour
    public static final long SHORT_CACHE_TTL = 300;     // 5 minutes
    public static final long LONG_CACHE_TTL = 86400;    // 24 hours

    // HTTP Methods
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String PATCH = "PATCH";
    public static final String DELETE = "DELETE";

    // Common Success Messages
    public static final String MSG_SUCCESS = "Operation completed successfully";
    public static final String MSG_CREATED = "Resource created successfully";
    public static final String MSG_UPDATED = "Resource updated successfully";
    public static final String MSG_DELETED = "Resource deleted successfully";

    // Rate Limiting
    public static final int RATE_LIMIT_REQUESTS = 100;
    public static final int RATE_LIMIT_DURATION_SECONDS = 60;
}

















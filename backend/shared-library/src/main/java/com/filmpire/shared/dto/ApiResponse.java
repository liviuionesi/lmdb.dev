package com.filmpire.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for all REST endpoints.
 * Provides a consistent response structure across all microservices.
 *
 * @param <T> the type of data being returned
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * Indicates whether the request was successful
     */
    private boolean success;

    /**
     * Human-readable message describing the response
     */
    private String message;

    /**
     * The actual response data (null if error)
     */
    private T data;

    /**
     * Timestamp when the response was generated
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * HTTP status code
     */
    private int statusCode;

    /**
     * Request path for debugging purposes
     */
    private String path;

    /**
     * Creates a successful response with data
     *
     * @param data       the response data
     * @param message    success message
     * @param statusCode HTTP status code
     * @param <T>        data type
     * @return successful ApiResponse
     */
    public static <T> ApiResponse<T> success(T data, String message, int statusCode) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(statusCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates a successful response with data and default message
     *
     * @param data the response data
     * @param <T>  data type
     * @return successful ApiResponse with 200 status
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Request successful", 200);
    }

    /**
     * Creates an error response without data
     *
     * @param message    error message
     * @param statusCode HTTP status code
     * @param <T>        data type
     * @return error ApiResponse
     */
    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .statusCode(statusCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with path information
     *
     * @param message    error message
     * @param statusCode HTTP status code
     * @param path       request path
     * @param <T>        data type
     * @return error ApiResponse with path
     */
    public static <T> ApiResponse<T> error(String message, int statusCode, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .statusCode(statusCode)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
}












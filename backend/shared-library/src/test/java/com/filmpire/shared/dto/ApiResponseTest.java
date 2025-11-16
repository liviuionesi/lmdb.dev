package com.filmpire.shared.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ApiResponse}.
 * <p>
 * This test class verifies the functionality of the ApiResponse DTO, including:
 * <ul>
 *   <li>Success response creation with custom and default messages</li>
 *   <li>Error response creation with and without path information</li>
 *   <li>Builder pattern functionality</li>
 *   <li>Timestamp generation</li>
 * </ul>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 * @see ApiResponse
 */
class ApiResponseTest {

    /**
     * Tests that {@link ApiResponse#success(Object, String, int)} creates a successful response
     * with all specified parameters including data, message, and status code.
     * Verifies that the response has success flag set to true, contains the expected data,
     * message, status code, and a non-null timestamp.
     */
    @Test
    void success_withData_shouldCreateSuccessResponse() {
        String data = "test data";
        ApiResponse<String> response = ApiResponse.success(data, "Success", 200);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getMessage()).isEqualTo("Success");
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getTimestamp()).isNotNull();
    }

    /**
     * Tests that {@link ApiResponse#success(Object)} creates a successful response
     * with default message "Request successful" and status code 200.
     * Verifies that the response has success flag set to true and uses default values
     * when only data is provided.
     */
    @Test
    void success_withDefaultMessage_shouldCreateSuccessResponse() {
        String data = "test data";
        ApiResponse<String> response = ApiResponse.success(data);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getMessage()).isEqualTo("Request successful");
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    /**
     * Tests that {@link ApiResponse#error(String, int)} creates an error response
     * with the specified error message and status code.
     * Verifies that the response has success flag set to false, data is null,
     * message and status code match the provided values, and timestamp is generated.
     */
    @Test
    void error_withStatusCode_shouldCreateErrorResponse() {
        ApiResponse<String> response = ApiResponse.error("Error occurred", 500);
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Error occurred");
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getTimestamp()).isNotNull();
    }

    /**
     * Tests that {@link ApiResponse#error(String, int, String)} creates an error response
     * with message, status code, and request path information.
     * Verifies that the response has success flag set to false, contains the error message,
     * status code, and the request path for debugging purposes.
     */
    @Test
    void error_withPath_shouldCreateErrorResponseWithPath() {
        ApiResponse<String> response = ApiResponse.error("Not found", 404, "/api/users/123");
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Not found");
        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getPath()).isEqualTo("/api/users/123");
    }

    /**
     * Tests that the builder pattern allows creating a fully customized ApiResponse
     * with all fields set to specific values.
     * Verifies that the builder correctly sets success flag, message, data, status code,
     * and path, demonstrating the flexibility of the builder pattern.
     */
    @Test
    void builder_shouldCreateCustomResponse() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Custom message")
                .data("custom data")
                .statusCode(201)
                .path("/api/resource")
                .build();
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("custom data");
        assertThat(response.getMessage()).isEqualTo("Custom message");
        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getPath()).isEqualTo("/api/resource");
    }
}

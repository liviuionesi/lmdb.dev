package com.filmpire.gateway.exception;

import tools.jackson.databind.ObjectMapper;
import com.filmpire.shared.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Global error handler for API Gateway.
 * Handles all exceptions and returns standardized error responses.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handles web exceptions and returns standardized error responses
     *
     * @param exchange the current server exchange
     * @param ex       the exception to handle
     * @return Mono<Void> representing the completion of error handling
     */
    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // Determine HTTP status and error message
        HttpStatus httpStatus;
        String errorMessage;
        String errorCode;

        if (ex instanceof ResponseStatusException responseStatusException) {
            httpStatus = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            errorMessage = responseStatusException.getReason();
            errorCode = httpStatus.name();
        } else if (ex instanceof io.jsonwebtoken.JwtException) {
            httpStatus = HttpStatus.UNAUTHORIZED;
            errorMessage = "Invalid or expired JWT token";
            errorCode = "JWT_ERROR";
        } else if (ex instanceof IllegalArgumentException) {
            httpStatus = HttpStatus.BAD_REQUEST;
            errorMessage = ex.getMessage();
            errorCode = "BAD_REQUEST";
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = "An unexpected error occurred";
            errorCode = "INTERNAL_SERVER_ERROR";
        }

        // Log the error
        log.error("Error handling request: {} - Status: {} - Message: {}",
                exchange.getRequest().getURI().getPath(),
                httpStatus.value(),
                errorMessage,
                ex);

        // Create error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(httpStatus.value())
                .errorCode(errorCode)
                .message(errorMessage)
                .path(exchange.getRequest().getURI().getPath())
                .build();

        // Set response status and content type
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Write error response
        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            byte[] bytes = Objects.requireNonNull(json.getBytes(StandardCharsets.UTF_8));
            DataBuffer buffer = Objects.requireNonNull(response.bufferFactory().wrap(bytes));
            return response.writeWith(Objects.requireNonNull(Mono.just(buffer)));
        } catch (JacksonException e) {
            log.error("Error serializing error response", e);
            return response.setComplete();
        }
    }
}


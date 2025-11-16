package com.filmpire.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter for API Gateway.
 * Logs all incoming requests and outgoing responses for monitoring and debugging.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    /**
     * Filters requests and responses to log information
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return Mono<Void> representing the completion of filter processing
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Log request details
        logRequest(request);

        // Record start time
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put("startTime", startTime);

        // Continue filter chain and log response
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    Long start = exchange.getAttribute("startTime");
                    if (start != null) {
                        long duration = System.currentTimeMillis() - start;
                        logResponse(request, response, duration);
                    }
                });
    }

    /**
     * Logs incoming request details
     *
     * @param request the server HTTP request
     */
    private void logRequest(ServerHttpRequest request) {
        String method = request.getMethod().toString();
        String path = request.getURI().getPath();
        String queryParams = request.getURI().getQuery();
        HttpHeaders headers = request.getHeaders();

        log.info("==> Incoming Request: {} {}", method, path);
        if (queryParams != null && !queryParams.isEmpty()) {
            log.debug("Query Parameters: {}", queryParams);
        }
        
        // Log important headers (avoid logging sensitive information)
        if (headers.containsKey("User-Agent")) {
            log.debug("User-Agent: {}", headers.getFirst("User-Agent"));
        }
        if (headers.containsKey("X-Forwarded-For")) {
            log.debug("X-Forwarded-For: {}", headers.getFirst("X-Forwarded-For"));
        }
    }

    /**
     * Logs outgoing response details
     *
     * @param request  the server HTTP request
     * @param response the server HTTP response
     * @param duration the request processing duration in milliseconds
     */
    private void logResponse(ServerHttpRequest request, ServerHttpResponse response, long duration) {
        String method = request.getMethod().toString();
        String path = request.getURI().getPath();
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

        log.info("<== Outgoing Response: {} {} - Status: {} - Duration: {}ms",
                method, path, statusCode, duration);

        // Log slow requests (> 1 second)
        if (duration > 1000) {
            log.warn("SLOW REQUEST DETECTED: {} {} took {}ms", method, path, duration);
        }
    }

    /**
     * Sets the filter order (executes early in the filter chain)
     *
     * @return the order value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}


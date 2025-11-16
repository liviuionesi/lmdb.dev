package com.filmpire.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Global rate limiting filter to prevent DDoS attacks.
 * Applies a global rate limit per IP address across all routes.
 * 
 * This provides an additional layer of protection beyond per-service rate limits:
 * - Prevents attackers from bypassing limits by hitting multiple services
 * - Sets a hard cap on total requests per IP per second
 * - Works in conjunction with service-specific rate limits
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.redis.core.ReactiveRedisTemplate.class)
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    private boolean enabled = true;
    
    // Global rate limit: 100 requests per second per IP
    private static final long GLOBAL_LIMIT = 100;
    private static final String REDIS_KEY_PREFIX = "global-rate-limit:";

    /**
     * Filters requests to enforce global rate limiting
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return Mono<Void> representing the completion of filter processing
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if disabled (e.g., in tests)
        if (!enabled) {
            return chain.filter(exchange);
        }
        
        // Skip rate limiting for actuator endpoints
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);
        String redisKey = REDIS_KEY_PREFIX + clientIp;

        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(count -> {
                    // Set expiration on first increment
                    if (count == 1) {
                        redisTemplate.expire(redisKey, Duration.ofSeconds(1)).subscribe();
                    }

                    // Check if limit exceeded
                    if (count > GLOBAL_LIMIT) {
                        log.warn("Global rate limit exceeded for IP: {} - {} requests/sec (limit: {})", 
                                clientIp, count, GLOBAL_LIMIT);
                        
                        // Set response headers
                        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(GLOBAL_LIMIT));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                        exchange.getResponse().getHeaders().set("X-RateLimit-Reset", "1");
                        
                        // Return 429 Too Many Requests
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    // Add rate limit headers
                    long remaining = GLOBAL_LIMIT - count;
                    exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(GLOBAL_LIMIT));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));
                    
                    log.debug("Global rate limit check passed for IP: {} - {}/{} requests", 
                            clientIp, count, GLOBAL_LIMIT);

                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    // If Redis is down, allow the request to continue
                    // (fail open to prevent service disruption)
                    log.error("Redis error in global rate limiting: {}. Allowing request.", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * Gets the client IP address from the request.
     * Checks X-Forwarded-For header first (for proxied requests), then remote address.
     *
     * @param exchange the server web exchange
     * @return the client IP address
     */
    private String getClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Get first IP from comma-separated list
            return forwardedFor.split(",")[0].trim();
        }

        // Fallback to remote address
        return exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();
    }

    /**
     * Sets the filter order (executes early in the filter chain, before authentication)
     *
     * @return the order value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2; // After logging filter
    }
}


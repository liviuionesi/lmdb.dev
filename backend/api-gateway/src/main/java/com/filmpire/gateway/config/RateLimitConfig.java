package com.filmpire.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration for API Gateway.
 * Implements IP-based rate limiting to prevent DDoS attacks.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    /**
     * IP-based key resolver for rate limiting.
     * Keys rate limits by client IP address from X-Forwarded-For header or remote address.
     * 
     * This provides per-IP rate limiting to prevent DDoS attacks where:
     * - Single IP cannot exceed rate limits
     * - Distributed attacks are mitigated per-IP
     * - Works with proxies (X-Forwarded-For header)
     *
     * @return KeyResolver that extracts client IP address
     */
    @Primary
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // Try to get IP from X-Forwarded-For header (for proxied requests)
            String forwardedFor = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Forwarded-For");

            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
                // We want the first one (original client IP)
                String clientIp = forwardedFor.split(",")[0].trim();
                log.debug("Rate limiting key from X-Forwarded-For: {}", clientIp);
                return Mono.just(clientIp);
            }

            // Fallback to remote address if no X-Forwarded-For header
            String remoteAddress = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
            
            log.debug("Rate limiting key from remote address: {}", remoteAddress);
            return Mono.just(remoteAddress);
        };
    }

    /**
     * Alternative key resolver based on user authentication.
     * Can be used for authenticated endpoints to rate limit by user ID.
     * 
     * This is not the primary resolver but can be referenced in specific routes.
     *
     * @return KeyResolver that extracts user ID from authentication
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> {
                    log.debug("Rate limiting key from user: {}", principal.getName());
                    return principal.getName();
                })
                .switchIfEmpty(Mono.just("anonymous"));
    }

    /**
     * Path-based key resolver for route-specific rate limiting.
     * Combines IP with path to provide per-endpoint rate limiting.
     *
     * @return KeyResolver that combines IP and path
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
            String path = exchange.getRequest().getURI().getPath();
            String key = ip + ":" + path;
            
            log.debug("Rate limiting key from IP+Path: {}", key);
            return Mono.just(key);
        };
    }
}


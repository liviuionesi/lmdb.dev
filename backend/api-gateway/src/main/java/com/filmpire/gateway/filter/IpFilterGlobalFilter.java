package com.filmpire.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP filtering global filter for blacklist/whitelist functionality.
 * Blocks requests from blacklisted IPs and optionally restricts access to whitelisted IPs only.
 * 
 * Features:
 * - IP blacklist for blocking malicious IPs
 * - IP whitelist for restricting access to trusted IPs (when enabled)
 * - Thread-safe concurrent implementation
 * - Logging of blocked requests for security monitoring
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class IpFilterGlobalFilter implements GlobalFilter, Ordered {

    // Thread-safe sets for blacklist and whitelist
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    
    // Control whether whitelist mode is enabled
    private boolean whitelistModeEnabled = false;

    /**
     * Filters requests based on IP blacklist/whitelist
     *
     * @param exchange the current server exchange
     * @param chain    the gateway filter chain
     * @return Mono<Void> representing the completion of filter processing
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip filtering for actuator endpoints (admin access)
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);

        // Check whitelist mode (if enabled, only whitelisted IPs are allowed)
        if (whitelistModeEnabled && !whitelist.isEmpty()) {
            if (!whitelist.contains(clientIp)) {
                log.warn("Access denied for non-whitelisted IP: {}", clientIp);
                return denyAccess(exchange, "IP not in whitelist");
            }
        }

        // Check blacklist
        if (blacklist.contains(clientIp)) {
            log.warn("Access denied for blacklisted IP: {}", clientIp);
            return denyAccess(exchange, "IP is blacklisted");
        }

        // IP is allowed, continue filter chain
        return chain.filter(exchange);
    }

    /**
     * Denies access by returning HTTP 403 Forbidden
     *
     * @param exchange the server web exchange
     * @param reason   the reason for denial
     * @return Mono<Void> with forbidden response
     */
    private Mono<Void> denyAccess(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().set("X-Blocked-Reason", reason);
        return exchange.getResponse().setComplete();
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
            return forwardedFor.split(",")[0].trim();
        }

        return exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();
    }

    /**
     * Adds an IP address to the blacklist
     *
     * @param ip the IP address to blacklist
     */
    public void addToBlacklist(String ip) {
        blacklist.add(ip);
        log.info("Added IP to blacklist: {}", ip);
    }

    /**
     * Removes an IP address from the blacklist
     *
     * @param ip the IP address to remove
     */
    public void removeFromBlacklist(String ip) {
        blacklist.remove(ip);
        log.info("Removed IP from blacklist: {}", ip);
    }

    /**
     * Adds an IP address to the whitelist
     *
     * @param ip the IP address to whitelist
     */
    public void addToWhitelist(String ip) {
        whitelist.add(ip);
        log.info("Added IP to whitelist: {}", ip);
    }

    /**
     * Removes an IP address from the whitelist
     *
     * @param ip the IP address to remove
     */
    public void removeFromWhitelist(String ip) {
        whitelist.remove(ip);
        log.info("Removed IP from whitelist: {}", ip);
    }

    /**
     * Enables whitelist mode (only whitelisted IPs are allowed)
     */
    public void enableWhitelistMode() {
        whitelistModeEnabled = true;
        log.info("Whitelist mode enabled - only whitelisted IPs will be allowed");
    }

    /**
     * Disables whitelist mode
     */
    public void disableWhitelistMode() {
        whitelistModeEnabled = false;
        log.info("Whitelist mode disabled");
    }

    /**
     * Gets the current blacklist
     *
     * @return set of blacklisted IPs
     */
    public Set<String> getBlacklist() {
        return Set.copyOf(blacklist);
    }

    /**
     * Gets the current whitelist
     *
     * @return set of whitelisted IPs
     */
    public Set<String> getWhitelist() {
        return Set.copyOf(whitelist);
    }

    /**
     * Checks if whitelist mode is enabled
     *
     * @return true if whitelist mode is enabled
     */
    public boolean isWhitelistModeEnabled() {
        return whitelistModeEnabled;
    }

    /**
     * Sets the filter order (executes first in the filter chain)
     *
     * @return the order value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Execute before all other filters
    }
}


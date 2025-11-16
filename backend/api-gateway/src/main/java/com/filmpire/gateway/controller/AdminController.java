package com.filmpire.gateway.controller;

import com.filmpire.gateway.filter.IpFilterGlobalFilter;
import com.filmpire.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Admin controller for managing IP filters and security settings.
 * Provides REST API for blacklist/whitelist management.
 * 
 * Note: These endpoints should be secured with admin-only access in production.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/security")
@RequiredArgsConstructor
public class AdminController {

    private final IpFilterGlobalFilter ipFilterGlobalFilter;

    /**
     * Get current blacklist
     *
     * @return blacklisted IPs
     */
    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<Set<String>>> getBlacklist() {
        Set<String> blacklist = ipFilterGlobalFilter.getBlacklist();
        return ResponseEntity.ok(ApiResponse.success(blacklist, "Current blacklist", 200));
    }

    /**
     * Add IP to blacklist
     *
     * @param ip the IP address to blacklist
     * @return success response
     */
    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<Void>> addToBlacklist(@RequestParam String ip) {
        ipFilterGlobalFilter.addToBlacklist(ip);
        log.info("Admin action: Added IP {} to blacklist", ip);
        return ResponseEntity.ok(ApiResponse.success(null, "IP added to blacklist", 200));
    }

    /**
     * Remove IP from blacklist
     *
     * @param ip the IP address to remove
     * @return success response
     */
    @DeleteMapping("/blacklist")
    public ResponseEntity<ApiResponse<Void>> removeFromBlacklist(@RequestParam String ip) {
        ipFilterGlobalFilter.removeFromBlacklist(ip);
        log.info("Admin action: Removed IP {} from blacklist", ip);
        return ResponseEntity.ok(ApiResponse.success(null, "IP removed from blacklist", 200));
    }

    /**
     * Get current whitelist
     *
     * @return whitelisted IPs
     */
    @GetMapping("/whitelist")
    public ResponseEntity<ApiResponse<Set<String>>> getWhitelist() {
        Set<String> whitelist = ipFilterGlobalFilter.getWhitelist();
        return ResponseEntity.ok(ApiResponse.success(whitelist, "Current whitelist", 200));
    }

    /**
     * Add IP to whitelist
     *
     * @param ip the IP address to whitelist
     * @return success response
     */
    @PostMapping("/whitelist")
    public ResponseEntity<ApiResponse<Void>> addToWhitelist(@RequestParam String ip) {
        ipFilterGlobalFilter.addToWhitelist(ip);
        log.info("Admin action: Added IP {} to whitelist", ip);
        return ResponseEntity.ok(ApiResponse.success(null, "IP added to whitelist", 200));
    }

    /**
     * Remove IP from whitelist
     *
     * @param ip the IP address to remove
     * @return success response
     */
    @DeleteMapping("/whitelist")
    public ResponseEntity<ApiResponse<Void>> removeFromWhitelist(@RequestParam String ip) {
        ipFilterGlobalFilter.removeFromWhitelist(ip);
        log.info("Admin action: Removed IP {} from whitelist", ip);
        return ResponseEntity.ok(ApiResponse.success(null, "IP removed from whitelist", 200));
    }

    /**
     * Enable whitelist mode (only whitelisted IPs allowed)
     *
     * @return success response
     */
    @PostMapping("/whitelist-mode/enable")
    public ResponseEntity<ApiResponse<Void>> enableWhitelistMode() {
        ipFilterGlobalFilter.enableWhitelistMode();
        log.warn("Admin action: WHITELIST MODE ENABLED - Only whitelisted IPs will be allowed");
        return ResponseEntity.ok(ApiResponse.success(null, "Whitelist mode enabled", 200));
    }

    /**
     * Disable whitelist mode
     *
     * @return success response
     */
    @PostMapping("/whitelist-mode/disable")
    public ResponseEntity<ApiResponse<Void>> disableWhitelistMode() {
        ipFilterGlobalFilter.disableWhitelistMode();
        log.info("Admin action: Whitelist mode disabled");
        return ResponseEntity.ok(ApiResponse.success(null, "Whitelist mode disabled", 200));
    }

    /**
     * Get security status
     *
     * @return security status information
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSecurityStatus() {
        Map<String, Object> status = Map.of(
                "blacklistSize", ipFilterGlobalFilter.getBlacklist().size(),
                "whitelistSize", ipFilterGlobalFilter.getWhitelist().size(),
                "whitelistModeEnabled", ipFilterGlobalFilter.isWhitelistModeEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(status, "Security status", 200));
    }
}


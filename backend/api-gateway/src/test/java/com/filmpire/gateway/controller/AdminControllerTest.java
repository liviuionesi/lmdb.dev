package com.filmpire.gateway.controller;

import com.filmpire.gateway.filter.IpFilterGlobalFilter;
import com.filmpire.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminController}, the runtime IP-security management
 * API over {@link IpFilterGlobalFilter}.
 *
 * <p>Wired with a real filter (not a mock) so each test asserts BOTH halves of
 * the contract: the controller returns a well-formed {@link ApiResponse}, AND
 * the operation actually mutated the underlying filter state. That end-to-end
 * check matters because these endpoints let an operator change the gateway's
 * security posture live — a response that claims success without applying the
 * change would be a dangerous silent failure.</p>
 */
@DisplayName("AdminController Tests")
class AdminControllerTest {

    private AdminController adminController;
    private IpFilterGlobalFilter ipFilterGlobalFilter;

    /** Fresh filter (empty lists, whitelist mode off) wrapped by the controller. */
    @BeforeEach
    void setUp() {
        ipFilterGlobalFilter = new IpFilterGlobalFilter();
        adminController = new AdminController(ipFilterGlobalFilter);
    }

    /**
     * A fresh gateway must report an empty blacklist — the baseline before any
     * management action, so later add/remove assertions start from a known state.
     */
    @Test
    @DisplayName("Should get empty blacklist initially")
    void getBlacklist_shouldReturnEmptyBlacklist() {
        // When
        ResponseEntity<ApiResponse<Set<String>>> response = adminController.getBlacklist();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Set<String>> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isEmpty();
    }

    /**
     * Adding an IP must both return success AND land in the filter's blacklist
     * — the follow-up filter check is what proves the endpoint has real effect,
     * not just a cosmetic OK response.
     */
    @Test
    @DisplayName("Should add IP to blacklist")
    void addToBlacklist_shouldAddIp() {
        // Given
        String ip = "192.168.1.100";

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.addToBlacklist(ip);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("blacklist");
        
        // Verify IP was added
        assertThat(ipFilterGlobalFilter.getBlacklist()).contains(ip);
    }

    /**
     * Removing a previously-blacklisted IP must clear it from the filter, so an
     * operator can actually un-block an address — verified against the filter,
     * not just the response.
     */
    @Test
    @DisplayName("Should remove IP from blacklist")
    void removeFromBlacklist_shouldRemoveIp() {
        // Given
        String ip = "192.168.1.100";
        ipFilterGlobalFilter.addToBlacklist(ip);

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.removeFromBlacklist(ip);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("removed");
        
        // Verify IP was removed
        assertThat(ipFilterGlobalFilter.getBlacklist()).doesNotContain(ip);
    }

    /**
     * The whitelist must also start empty — baseline for the whitelist
     * management tests, mirroring the blacklist baseline.
     */
    @Test
    @DisplayName("Should get empty whitelist initially")
    void getWhitelist_shouldReturnEmptyWhitelist() {
        // When
        ResponseEntity<ApiResponse<Set<String>>> response = adminController.getWhitelist();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Set<String>> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isEmpty();
    }

    /**
     * Adding to the whitelist must reach the filter's whitelist specifically
     * (not the blacklist) — the separate assertion guards against the two
     * management paths being crossed.
     */
    @Test
    @DisplayName("Should add IP to whitelist")
    void addToWhitelist_shouldAddIp() {
        // Given
        String ip = "10.0.0.1";

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.addToWhitelist(ip);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("whitelist");
        
        // Verify IP was added
        assertThat(ipFilterGlobalFilter.getWhitelist()).contains(ip);
    }

    /**
     * Removing from the whitelist must clear the entry from the filter, the
     * whitelist counterpart to blacklist removal.
     */
    @Test
    @DisplayName("Should remove IP from whitelist")
    void removeFromWhitelist_shouldRemoveIp() {
        // Given
        String ip = "10.0.0.1";
        ipFilterGlobalFilter.addToWhitelist(ip);

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.removeFromWhitelist(ip);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("removed");
        
        // Verify IP was removed
        assertThat(ipFilterGlobalFilter.getWhitelist()).doesNotContain(ip);
    }

    /**
     * Enabling whitelist mode flips the gateway from deny-list to allow-list
     * enforcement — a high-impact switch, so the test confirms the flag
     * actually changed on the filter, not merely that the endpoint answered OK.
     */
    @Test
    @DisplayName("Should enable whitelist mode")
    void enableWhitelistMode_shouldEnable() {
        // Given
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isFalse();

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.enableWhitelistMode();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("enabled");
        
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isTrue();
    }

    /**
     * Disabling whitelist mode must return the gateway to blacklist
     * enforcement — the reverse switch, verified on the filter flag so the
     * mode can be toggled back safely.
     */
    @Test
    @DisplayName("Should disable whitelist mode")
    void disableWhitelistMode_shouldDisable() {
        // Given
        ipFilterGlobalFilter.enableWhitelistMode();
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isTrue();

        // When
        ResponseEntity<ApiResponse<Void>> response = adminController.disableWhitelistMode();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Void> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getMessage()).contains("disabled");
        
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isFalse();
    }

    /**
     * The status endpoint must report the live security posture accurately:
     * with two blacklisted IPs, one whitelisted, and whitelist mode on, the
     * returned sizes and flag must match exactly — this is the dashboard an
     * operator trusts, so wrong counts would mislead security decisions.
     */
    @Test
    @DisplayName("Should get security status")
    void getSecurityStatus_shouldReturnStatus() {
        // Given
        ipFilterGlobalFilter.addToBlacklist("192.168.1.1");
        ipFilterGlobalFilter.addToBlacklist("192.168.1.2");
        ipFilterGlobalFilter.addToWhitelist("10.0.0.1");
        ipFilterGlobalFilter.enableWhitelistMode();

        // When
        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.getSecurityStatus();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<Map<String, Object>> body = Objects.requireNonNull(
                response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isTrue();
        
        Map<String, Object> status = body.getData();
        assertThat(status)
                .isNotNull()
                .containsEntry("blacklistSize", 2)
                .containsEntry("whitelistSize", 1)
                .containsEntry("whitelistModeEnabled", true);
    }

    /**
     * The blacklist must behave correctly at set scale: adding three IPs then
     * removing one must leave exactly the other two, proving the underlying set
     * handles multiple entries and selective removal (not just single-entry
     * happy paths).
     */
    @Test
    @DisplayName("Should manage multiple IPs in blacklist")
    void blacklist_shouldManageMultipleIps() {
        // Given
        String[] ips = {"192.168.1.1", "192.168.1.2", "192.168.1.3"};

        // When
        for (String ip : ips) {
            adminController.addToBlacklist(ip);
        }

        // Then
        assertThat(ipFilterGlobalFilter.getBlacklist()).hasSize(3);
        assertThat(ipFilterGlobalFilter.getBlacklist()).containsExactlyInAnyOrder(ips);

        // When - Remove one
        adminController.removeFromBlacklist("192.168.1.2");

        // Then
        assertThat(ipFilterGlobalFilter.getBlacklist()).hasSize(2);
        assertThat(ipFilterGlobalFilter.getBlacklist()).containsExactlyInAnyOrder("192.168.1.1", "192.168.1.3");
    }

    /**
     * The whitelist counterpart of the multi-IP test: three adds then one
     * removal must leave exactly two, confirming multi-entry management works
     * independently for the whitelist.
     */
    @Test
    @DisplayName("Should manage multiple IPs in whitelist")
    void whitelist_shouldManageMultipleIps() {
        // Given
        String[] ips = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};

        // When
        for (String ip : ips) {
            adminController.addToWhitelist(ip);
        }

        // Then
        assertThat(ipFilterGlobalFilter.getWhitelist()).hasSize(3);
        assertThat(ipFilterGlobalFilter.getWhitelist()).containsExactlyInAnyOrder(ips);

        // When - Remove one
        adminController.removeFromWhitelist("10.0.0.2");

        // Then
        assertThat(ipFilterGlobalFilter.getWhitelist()).hasSize(2);
        assertThat(ipFilterGlobalFilter.getWhitelist()).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.3");
    }
}















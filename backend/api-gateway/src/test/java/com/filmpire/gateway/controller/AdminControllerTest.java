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
 * Unit tests for AdminController.
 */
@DisplayName("AdminController Tests")
class AdminControllerTest {

    private AdminController adminController;
    private IpFilterGlobalFilter ipFilterGlobalFilter;

    @BeforeEach
    void setUp() {
        ipFilterGlobalFilter = new IpFilterGlobalFilter();
        adminController = new AdminController(ipFilterGlobalFilter);
    }

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
        assertThat(status).isNotNull();
        assertThat(status.get("blacklistSize")).isEqualTo(2);
        assertThat(status.get("whitelistSize")).isEqualTo(1);
        assertThat(status.get("whitelistModeEnabled")).isEqualTo(true);
    }

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


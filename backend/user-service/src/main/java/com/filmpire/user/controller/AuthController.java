package com.filmpire.user.controller;

import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.user.dto.AuthDtos.AuthResponse;
import com.filmpire.user.dto.AuthDtos.LoginRequest;
import com.filmpire.user.dto.AuthDtos.RefreshRequest;
import com.filmpire.user.dto.AuthDtos.RegisterRequest;
import com.filmpire.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (issue #17): register, login, refresh, logout.
 *
 * <p>All routes under {@code /api/v1/auth} are public except logout, which
 * requires the caller's access token (SecurityConfig permits the path;
 * logout itself reads the authenticated principal and no-ops without one).
 * Responses use the shared {@link ApiResponse} envelope like every native
 * Filmpire endpoint.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new account and returns an immediate token bundle.
     *
     * @param request validated registration payload
     * @return 201 with access + refresh tokens and the new profile
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse auth = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(auth, "User registered successfully", HttpStatus.CREATED.value()));
    }

    /**
     * Authenticates credentials and returns a token bundle.
     *
     * @param request username + password
     * @return 200 with access + refresh tokens
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(
            ApiResponse.success(auth, "Login successful", HttpStatus.OK.value()));
    }

    /**
     * Exchanges a refresh token for a new token pair (rotation — the
     * presented refresh token is invalidated).
     *
     * @param request the opaque refresh token
     * @return 200 with a brand-new token bundle
     */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and obtain a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        AuthResponse auth = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(
            ApiResponse.success(auth, "Token refreshed", HttpStatus.OK.value()));
    }

    /**
     * Logs out the authenticated caller by revoking all their refresh
     * tokens. The current access token stays valid until natural expiry —
     * a documented property of stateless JWTs.
     *
     * @param authentication caller identity from the JWT filter (may be null
     *                       when called without a token — then a no-op)
     * @return 200 acknowledgement
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke all refresh tokens of the current user")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        if (authentication != null) {
            authService.logout(authentication.getName());
        }
        return ResponseEntity.ok(
            ApiResponse.success(null, "Logged out", HttpStatus.OK.value()));
    }
}

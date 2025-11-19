package com.filmpire.gateway.config;

import com.filmpire.gateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for API Gateway.
 * Configures JWT authentication, CORS, and public/private endpoints.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configures security filter chain for the API Gateway
     *
     * @param http the ServerHttpSecurity to configure
     * @return the configured SecurityWebFilterChain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF for stateless API
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Configure authorization
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        
                        // Public GET endpoints (read-only access)
                        .pathMatchers(HttpMethod.GET, "/api/v1/movies/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/genres/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/actors/**").permitAll()
                        
                        // Admin endpoints (require authentication - should add role check in production)
                        .pathMatchers("/admin/**").authenticated()
                        
                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )
                
                // Add JWT authentication filter
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                
                .build();
    }

    /**
     * Configures CORS for the API Gateway
     *
     * @return CorsConfigurationSource with allowed origins, methods, and headers
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (frontend applications)
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",  // React default
                "http://localhost:3001",  // Alternative React port
                "http://localhost:5173",  // Vite default
                "http://localhost:5174"   // Alternative Vite port
        ));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // How long the response from a pre-flight request can be cached
        configuration.setMaxAge(3600L);
        
        // Expose headers to the client
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Access-Control-Allow-Origin"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}


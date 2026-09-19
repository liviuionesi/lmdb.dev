package dev.lmdb.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Access control for the config endpoints (#244, ADR-022).
 *
 * <p>Whether the config endpoints require a credential is controlled by {@code
 * config.security.enabled}, off by default so local development needs no credentials. Exactly one
 * of the two {@link SecurityFilterChain} beans below is active at a time, chosen by that property.
 * Both leave {@code /actuator/**} open regardless, so health/readiness/liveness probes and the
 * Prometheus scrape never need a credential.
 */
@Configuration
@EnableWebSecurity
public class ConfigServerSecurityConfig {

  /**
   * Active when {@code config.security.enabled=true}: everything except {@code /actuator/**}
   * requires HTTP Basic auth against the single in-memory user defined by {@link
   * #configServerUser}.
   *
   * @param http the security builder
   * @return the enforcing filter chain
   * @throws Exception per Spring Security's builder contract
   */
  @Bean
  @ConditionalOnProperty(name = "config.security.enabled", havingValue = "true")
  public SecurityFilterChain securedConfigServerFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  /**
   * Active whenever {@link #securedConfigServerFilterChain} isn't — {@code config.security.enabled}
   * false, unset, or any other value that isn't exactly {@code true}. Matches this server's
   * behavior before access control existed: every request is permitted.
   *
   * <p>Deliberately keyed off {@link ConditionalOnMissingBean} rather than a second, mirrored
   * {@code @ConditionalOnProperty(havingValue = "false")}: a typo'd value (neither {@code true} nor
   * {@code false}) would then match neither bean, leaving this server with no {@link
   * SecurityFilterChain} at all — an undefined state, not a safely-open one.
   *
   * @param http the security builder
   * @return the permissive filter chain
   * @throws Exception per Spring Security's builder contract
   */
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain openConfigServerFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /**
   * The single credential config endpoints accept once access control is enabled. Read directly
   * from properties rather than Boot's {@code spring.security.user.*} mechanism so that {@code
   * config.security.password} is only ever resolved when this bean is actually created — a bare
   * {@code ${CONFIG_SECURITY_PASSWORD}} placeholder in {@code application.yml} with no default
   * would otherwise fail every local startup that hasn't set it.
   *
   * @param username the configured username
   * @param password the configured plaintext password, encoded before storage
   * @param passwordEncoder encoder used to store the password
   * @return an in-memory user store holding the one config-client user
   */
  @Bean
  @ConditionalOnProperty(name = "config.security.enabled", havingValue = "true")
  public InMemoryUserDetailsManager configServerUser(
      @Value("${config.security.username}") String username,
      @Value("${config.security.password}") String password,
      PasswordEncoder passwordEncoder) {
    UserDetails user =
        User.withUsername(username)
            .password(passwordEncoder.encode(password))
            .roles("CONFIG_CLIENT")
            .build();
    return new InMemoryUserDetailsManager(user);
  }

  /**
   * Shared password encoder for {@link #configServerUser}.
   *
   * @return Spring Security's recommended delegating encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}

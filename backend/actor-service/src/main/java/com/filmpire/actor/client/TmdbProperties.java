package com.filmpire.actor.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for the TMDB API connection, bound from {@code tmdb.api.*} in {@code
 * application.yml}.
 *
 * <p>Using a record (supported as of Spring Boot 3.x / 4.x) makes the properties immutable,
 * constructor-bound, and trivially testable without reflection — replacing the scattered
 * {@code @Value} fields that were previously spread across {@link TmdbClientConfig} and {@link
 * com.filmpire.actor.service.ActorService}.
 *
 * @param key server-side TMDB API key (never exposed to clients)
 * @param baseUrl TMDB API base URL (e.g. {@code https://api.themoviedb.org/3})
 * @param connectTimeout TCP connect timeout in milliseconds (default 5 000)
 * @param readTimeout socket read timeout in milliseconds (default 10 000)
 */
@ConfigurationProperties(prefix = "tmdb.api")
public record TmdbProperties(String key, String baseUrl, int connectTimeout, int readTimeout) {

  /**
   * Compact constructor applying defaults for optional timeout fields. Spring binds {@code
   * tmdb.api.connect-timeout} and {@code tmdb.api.read-timeout} when present; when absent, the
   * record component defaults cannot be expressed in the canonical constructor's signature, so we
   * apply them here.
   */
  public TmdbProperties {
    if (connectTimeout <= 0) connectTimeout = 5_000;
    if (readTimeout <= 0) readTimeout = 10_000;
  }
}

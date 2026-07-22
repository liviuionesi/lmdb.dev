package com.filmpire.movie.client;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Intercepts outbound Http requests to the TMDB API to enforce rate limits.
 * TMDB allows 40 requests per 10 seconds.
 */
@Component
@Slf4j
public class TmdbRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final Bucket bucket;

    public TmdbRateLimitInterceptor() {
        // TMDB Limit: 40 requests per 10 seconds
        Bandwidth limit = Bandwidth.builder()
                .capacity(40)
                .refillIntervally(40, Duration.ofSeconds(10))
                .build();
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, byte @NonNull[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        try {
            // Block thread until a token is available to avoid 429 Too Many Requests
            log.debug("Awaiting TMDB rate limit token for: {}", request.getURI());
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Rate limit interrupted", e);
            throw new IOException("Request interrupted while waiting for rate limit token", e);
        }

        return execution.execute(request, body);
    }
}

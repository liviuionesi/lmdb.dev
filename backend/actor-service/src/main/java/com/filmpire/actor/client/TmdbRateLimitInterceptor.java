package com.filmpire.actor.client;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Throttles outbound TMDB requests to stay under TMDB's rate limit
 * (40 requests / 10 seconds).
 *
 * <p>Mirrors movie-service's interceptor of the same name — each service
 * instance holds its OWN in-memory bucket, so the effective platform-wide
 * ceiling is 40 req/10 s per service instance. Deliberately duplicated
 * rather than shared: services share contracts, not implementation
 * (microservice boundary discipline); the contract tests (#43) keep the
 * copies honest.</p>
 */
@Component
@Slf4j
public class TmdbRateLimitInterceptor implements ClientHttpRequestInterceptor {

    /** In-memory token bucket: 40 tokens, refilled every 10 seconds. */
    private final Bucket bucket;

    /** Builds the bucket with TMDB's published limit. */
    public TmdbRateLimitInterceptor() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(40)
                .refillIntervally(40, Duration.ofSeconds(10))
                .build();
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Blocks until a token is available, then executes the request.
     *
     * @param request   outbound request
     * @param body      request body bytes
     * @param execution downstream execution chain
     * @return the upstream response
     * @throws IOException if interrupted while waiting or on I/O failure
     */
    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request,
                                        @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        try {
            // Block the calling thread until a token frees — better a slow
            // response than a 429 from TMDB.
            log.debug("Awaiting TMDB rate limit token for: {}", request.getURI());
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Rate limit wait interrupted", e);
            throw new IOException("Request interrupted while waiting for rate limit token", e);
        }

        return execution.execute(request, body);
    }
}

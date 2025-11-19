package com.filmpire.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation to apply rate limiting to a method or class.
 * Can be used on controller methods to limit the number of requests per time window.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @RateLimited(requests = 100, per = 1, timeUnit = TimeUnit.MINUTES)
 * @GetMapping("/api/movies")
 * public ResponseEntity getMovies() {
 *     // method implementation
 * }
 * }
 * </pre>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /**
     * Number of requests allowed
     *
     * @return request limit
     */
    int requests() default 100;

    /**
     * Time window duration
     *
     * @return duration value
     */
    long per() default 1;

    /**
     * Time unit for the time window
     *
     * @return time unit
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * Rate limit key prefix (useful for different endpoints)
     *
     * @return key prefix
     */
    String keyPrefix() default "";

    /**
     * Whether to apply rate limit per user (requires authentication)
     *
     * @return true to apply per user
     */
    boolean perUser() default false;
}












package com.filmpire.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation to cache method results.
 * Can be used on methods to automatically cache their return values.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @CacheResult(key = "movie", ttl = 3600)
 * public Movie getMovieById(String id) {
 *     // method implementation
 * }
 * }
 * </pre>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheResult {

    /**
     * Cache key prefix
     *
     * @return cache key prefix
     */
    String key();

    /**
     * Time to live for cache entry
     *
     * @return TTL value
     */
    long ttl() default 3600;

    /**
     * Time unit for TTL
     *
     * @return time unit
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Whether to cache null values
     *
     * @return true to cache null values
     */
    boolean cacheNull() default false;

    /**
     * Condition under which to cache (SpEL expression)
     *
     * @return SpEL condition
     */
    String condition() default "";
}




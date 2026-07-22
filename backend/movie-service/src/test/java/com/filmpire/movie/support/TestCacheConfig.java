package com.filmpire.movie.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * Supplies a lightweight in-memory {@link CacheManager} to {@code @WebMvcTest}
 * slices.
 *
 * <p>The application class is annotated {@code @EnableCaching}, so Spring's
 * caching infrastructure requires a {@link CacheManager} at context-refresh
 * time. The full-context tests get one from cache auto-configuration (driven by
 * {@code spring.cache.type}), but as of Spring Boot 4 the {@code @WebMvcTest}
 * slice no longer imports {@code CacheAutoConfiguration}, so web-layer tests
 * must provide their own. A {@link ConcurrentMapCacheManager} keeps these tests
 * hermetic — no Redis, no external infrastructure.</p>
 *
 * <p>Import it explicitly on the relevant slice test (it is not component-scanned
 * by the slice):
 * {@code @Import(TestCacheConfig.class)}.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestCacheConfig {

    /**
     * In-memory cache manager satisfying {@code @EnableCaching} in web slices.
     *
     * @return a {@link ConcurrentMapCacheManager} backing all cache names
     */
    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}

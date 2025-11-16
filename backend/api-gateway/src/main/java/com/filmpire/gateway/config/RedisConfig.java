package com.filmpire.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for API Gateway.
 * Provides ReactiveRedisTemplate for rate limiting and caching.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Configuration
@ConditionalOnBean(ReactiveRedisConnectionFactory.class)
public class RedisConfig {

    /**
     * Creates a ReactiveRedisTemplate for global rate limiting.
     * Uses String serializers for both keys and values.
     *
     * @param connectionFactory the reactive redis connection factory
     * @return ReactiveRedisTemplate for String operations
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer serializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}


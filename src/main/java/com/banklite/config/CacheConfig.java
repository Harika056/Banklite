package com.banklite.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Enables Spring Cache. Outside the "test" profile the cache backend is Redis with a short TTL.
 * The "test" profile defines no CacheManager bean here and sets spring.cache.type=none, so
 * Spring Boot falls back to a no-op cache and tests never need a Redis server.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ACCOUNTS_CACHE = "accounts";

    @Bean
    @Profile("!test")
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          @Value("${banklite.cache.accounts-ttl-seconds:60}") long ttlSeconds) {
        // Default JDK serialization: cached values (AccountResponse) implement Serializable.
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .prefixCacheNameWith("banklite:");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .withCacheConfiguration(ACCOUNTS_CACHE, configuration)
                .build();
    }
}

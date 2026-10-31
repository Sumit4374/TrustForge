package com.sumit.trustforge.config;

import com.sumit.trustforge.infrastructure.ReadCache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements org.springframework.cache.annotation.CachingConfigurer {
    @Override public org.springframework.cache.interceptor.CacheErrorHandler errorHandler() {
        return new org.springframework.cache.interceptor.CacheErrorHandler() {
            public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache c, Object key) {}
            public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache c, Object key, Object value) {}
            public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache c, Object key) {}
            public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache c) {}
        };
    }
    @Bean CacheManager cacheManager(ReadCache cache) {
        var strings = RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());
        var policy = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues().serializeKeysWith(strings).serializeValuesWith(strings)
                .computePrefixWith(name -> "tf:domain-cache:v1:" + name + ":");
        return RedisCacheManager.builder(cache.connection()).cacheDefaults(policy).build();
    }
}

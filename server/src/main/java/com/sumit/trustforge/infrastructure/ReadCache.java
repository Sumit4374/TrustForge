package com.sumit.trustforge.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Separate evictable connection; never used for sessions or authorization. */
@Component
public class ReadCache implements DisposableBean {
    private final LettuceConnectionFactory connection;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;

    public ReadCache(@Value("${trustforge.cache.host:localhost}") String host,
                     @Value("${trustforge.cache.port:6380}") int port,
                     @Value("${trustforge.cache.password:}") String password,
                     @Value("${trustforge.cache.tls:false}") boolean tls,
                     MeterRegistry metrics) {
        this.metrics = metrics;
        var server = new RedisStandaloneConfiguration(host, port);
        if (!password.isBlank()) server.setPassword(RedisPassword.of(password));
        var options = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1))
                .shutdownTimeout(Duration.ofMillis(100));
        if (tls) options.useSsl();
        connection = new LettuceConnectionFactory(server, options.build());
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
    }

    public String get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            metrics.counter("trustforge.cache.reads", "result", value == null ? "miss" : "hit").increment();
            return value;
        } catch (RuntimeException unavailable) {
            metrics.counter("trustforge.cache.reads", "result", "unavailable").increment();
            return null;
        }
    }

    public void put(String key, String json, Duration ttl) {
        if (json.length() > 65536) return;
        try { redis.opsForValue().set(key, json, ttl); }
        catch (RuntimeException unavailable) { metrics.counter("trustforge.cache.write.failures").increment(); }
    }

    public void delete(String key) {
        try { redis.delete(key); }
        catch (RuntimeException unavailable) { metrics.counter("trustforge.cache.write.failures").increment(); }
    }

    public boolean healthy() {
        try (var session = connection.getConnection()) { return "PONG".equals(session.ping()); }
        catch (RuntimeException unavailable) { return false; }
    }

    public LettuceConnectionFactory connection() { return connection; }
    @Override public void destroy() { connection.destroy(); }
}

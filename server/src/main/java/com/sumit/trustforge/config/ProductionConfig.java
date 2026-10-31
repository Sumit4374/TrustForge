package com.sumit.trustforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfig {
    public ProductionConfig(@Value("${trustforge.production}")boolean production,
        @Value("${server.servlet.session.cookie.secure}")boolean secure,
        @Value("${trustforge.public-url}")String baseUrl,
        @Value("${spring.datasource.password}")String dbPassword,
        @Value("${spring.data.redis.password}")String redisPassword,
        @Value("${spring.data.redis.ssl.enabled}")boolean redisTls,
        @Value("${trustforge.cache.password}")String cachePassword,
        @Value("${trustforge.cache.tls}")boolean cacheTls,
        @Value("${spring.mail.host}")String smtp,
        @Value("${spring.mail.properties.mail.smtp.starttls.enable}")boolean smtpTls){
        if(production&&(!secure||!baseUrl.startsWith("https://")||dbPassword.isBlank()||redisPassword.isBlank()||cachePassword.isBlank()||!redisTls||!cacheTls||smtp.isBlank()||!smtpTls))
            throw new IllegalStateException("Production requires HTTPS, secure cookies, database/Redis credentials, Redis TLS and SMTP TLS");
    }
}

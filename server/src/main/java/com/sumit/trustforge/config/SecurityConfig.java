package com.sumit.trustforge.config;

import com.sumit.trustforge.identity.SessionValidationFilter;
import com.sumit.trustforge.identity.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new Argon2PasswordEncoder(16,32,1,65536,3);}
    @Bean(name="springSessionDefaultRedisSerializer") RedisSerializer<Object> sessionSerializer(){return new SafeSessionSerializer();}
    @Bean SecurityContextRepository securityContextRepository(){return new HttpSessionSecurityContextRepository();}
    @Bean CsrfTokenRepository csrfTokenRepository(){return new HttpSessionCsrfTokenRepository();}
    @Bean org.springframework.security.core.session.SessionRegistry sessionRegistry(org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessions){
        return new org.springframework.session.security.SpringSessionBackedSessionRegistry<>(sessions);
    }
    @Bean org.springframework.security.web.authentication.session.SessionAuthenticationStrategy loginSessionStrategy(org.springframework.security.core.session.SessionRegistry registry){
        var concurrent=new org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy(registry);
        concurrent.setMaximumSessions(10);concurrent.setExceptionIfMaximumExceeded(true);
        return new org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy(java.util.List.of(concurrent,new org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy(),new org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy(registry)));
    }
    @Bean SecurityFilterChain security(HttpSecurity http,UserRepository users,SecurityContextRepository contexts,CsrfTokenRepository csrf,org.springframework.security.core.session.SessionRegistry registry)throws Exception{
        http.authorizeHttpRequests(a->a
            .requestMatchers("/actuator/health","/actuator/health/liveness","/actuator/health/readiness").permitAll()
            .requestMatchers("/api/v1/auth/csrf","/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/logout","/api/v1/auth/verify","/api/v1/auth/forgot-password","/api/v1/auth/reset-password","/api/v1/auth/resend-verification").permitAll()
            .requestMatchers("/actuator/**","/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll());
        http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.securityContext(c->c.securityContextRepository(contexts).requireExplicitSave(true));
        http.sessionManagement(s->s.sessionFixation(f->f.changeSessionId()).maximumSessions(10).maxSessionsPreventsLogin(true).sessionRegistry(registry));
        http.requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable());
        http.exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":401,\"title\":\"Authentication required\",\"detail\":\"Sign in to continue\"}");})
            .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":403,\"title\":\"Access denied\",\"detail\":\"Your session token or permission is invalid. Refresh and retry.\"}");}));
        http.headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
            .referrerPolicy(r->r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .frameOptions(f->f.deny()).httpStrictTransportSecurity(s->s.includeSubDomains(true).maxAgeInSeconds(31536000)));
        http.addFilterBefore(new RequestSecurityFilter(),AnonymousAuthenticationFilter.class);
        http.addFilterBefore(new SessionValidationFilter(users),AnonymousAuthenticationFilter.class);
        return http.build();
    }
}

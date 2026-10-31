package com.sumit.trustforge.infrastructure;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** Real separate Redis stores; failures affect only containers owned by this class. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"debug=false","trace=false"})
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CacheFailureIntegrationTests {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static final GenericContainer<?> sessions=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @Container static final GenericContainer<?> cache=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
        p.add("spring.data.redis.host",sessions::getHost);p.add("spring.data.redis.port",()->sessions.getMappedPort(6379));p.add("spring.data.redis.password",()->"");
        p.add("trustforge.cache.host",cache::getHost);p.add("trustforge.cache.port",()->cache.getMappedPort(6379));p.add("trustforge.cache.password",()->"");
        p.add("trustforge.production",()->false);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired MeterRegistry metrics;
    @Autowired ReadCache readCache;
    private static final String PASSWORD="synthetic-cache-test-password-123";

    class Browser {
        final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        HttpResponse<String> raw(String method,String path,Object body)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");
            if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int status)throws Exception{
            var response=raw(method,path,body);assertThat(response.statusCode()).as(method+" "+path).isEqualTo(status);
            return response.body().isEmpty()?json.nullNode():json.readTree(response.body());
        }
        void refresh()throws Exception{csrf=call("GET","/auth/csrf",null,200).path("token").asText();}
        void login(String email)throws Exception{
            refresh();call("POST","/auth/login",Map.of("email",email,"password",PASSWORD),200);refresh();
        }
        String project(String name)throws Exception{return call("POST","/projects",Map.of("name",name,"description","Synthetic cache isolation fixture"),201).path("id").asText();}
        int projects()throws Exception{return call("GET","/dashboard",null,200).path("projects").asInt();}
    }
    private UUID seed(String email,String encoded){
        UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,name,password_hash,verified,role) VALUES (?,?,?, ?,true,'USER')",id,email,"Cache test user",encoded);return id;
    }
    private double reads(String result){
        var counter=metrics.find("trustforge.cache.reads").tag("result",result).counter();return counter==null?0:counter.count();
    }
    private static void pause(GenericContainer<?> owned){owned.getDockerClient().pauseContainerCmd(owned.getContainerId()).exec();}
    private static void resume(GenericContainer<?> owned){owned.getDockerClient().unpauseContainerCmd(owned.getContainerId()).exec();}

    @Test void cacheIsScopedInvalidatedAndOptionalWhileSessionsFailClosed()throws Exception{
        String suffix=UUID.randomUUID().toString();String aliceEmail="cache-alice-"+suffix+"@example.test",bobEmail="cache-bob-"+suffix+"@example.test";
        String encoded=passwords.encode(PASSWORD);UUID aliceId=seed(aliceEmail,encoded),bobId=seed(bobEmail,encoded);
        Browser alice=new Browser(),bob=new Browser();alice.login(aliceEmail);bob.login(bobEmail);
        String first=alice.project("Private synthetic workspace A");alice.project("Private synthetic workspace B");
        var redis=new StringRedisTemplate(readCache.connection());
        String aliceKey="tf:ui-cache:v1:dashboard:"+aliceId,bobKey="tf:ui-cache:v1:dashboard:"+bobId;
        double initialMisses=reads("miss"),initialHits=reads("hit");
        assertThat(alice.projects()).isEqualTo(2);assertThat(reads("miss")-initialMisses).isEqualTo(1);
        assertThat(alice.projects()).isEqualTo(2);assertThat(reads("hit")-initialHits).isEqualTo(1);
        assertThat(bob.projects()).isZero();assertThat(reads("miss")-initialMisses).isEqualTo(2);
        assertThat(redis.hasKey(aliceKey)).isTrue();assertThat(redis.hasKey(bobKey)).isTrue();
        assertThat(redis.getExpire(aliceKey)).isBetween(1L,30L);
        assertThat(json.readTree(redis.opsForValue().get(aliceKey)).path("value").path("projects").asInt()).isEqualTo(2);
        assertThat(json.readTree(redis.opsForValue().get(bobKey)).path("value").path("projects").asInt()).isZero();
        alice.project("Private synthetic workspace C");
        assertThat(redis.hasKey(aliceKey)).isFalse();assertThat(alice.projects()).isEqualTo(3);
        assertThat(reads("miss")-initialMisses).isEqualTo(3);
        alice.call("POST","/projects/"+first+"/members",Map.of("email",bobEmail,"role","VIEWER"),204);
        assertThat(bob.projects()).isEqualTo(1);
        alice.call("DELETE","/projects/"+first+"/members/"+bobId,null,204);
        // A physical cache hit may contain an obsolete scope; authoritative membership must reject it.
        assertThat(bob.projects()).isZero();bob.call("GET","/projects/"+first,null,404);
        alice.call("GET","/catalog/categories",null,200);alice.call("GET","/catalog/categories",null,200);
        assertThat(redis.hasKey("tf:domain-cache:v1:categories:v2")).isTrue();
        assertThat(alice.projects()).isEqualTo(3);
        double misses=reads("miss")-initialMisses,hits=reads("hit")-initialHits;
        double initialUnavailable=reads("unavailable");
        pause(cache);
        try{
            assertThat(alice.projects()).isEqualTo(3);
            assertThat(reads("unavailable")-initialUnavailable).isEqualTo(1);
            // Spring Cache also falls back to its public deterministic metadata method.
            assertThat(alice.call("GET","/catalog/categories",null,200).isArray()).isTrue();
            alice.call("GET","/auth/me",null,200);
        }finally{resume(cache);}
        assertThat(readCache.healthy()).isTrue();assertThat(alice.projects()).isEqualTo(3);
        double hitsBeforeSessionFailure=reads("hit");
        int sessionOutageStatus;
        pause(sessions);
        try{
            var response=alice.raw("GET","/dashboard",null);
            sessionOutageStatus=response.statusCode();
            assertThat(sessionOutageStatus).isEqualTo(503);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(response.body()).contains("temporarily unavailable");
            assertThat(response.body()).doesNotContain("\"projects\"","\"recentRuns\"",aliceEmail,bobEmail);
            assertThat(reads("hit")).isEqualTo(hitsBeforeSessionFailure);
        }finally{resume(sessions);}
        alice.call("GET","/auth/me",null,200);assertThat(alice.projects()).isEqualTo(3);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("cache_verification measured_misses={} measured_hits={} cache_outage_fallbacks={} session_outage_denied=true session_outage_status={}",misses,hits,reads("unavailable")-initialUnavailable,sessionOutageStatus);
    }
}

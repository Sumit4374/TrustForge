package com.sumit.trustforge.config;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class RequestSecurityFilterTest {
    @Test void unknownLengthBodyCannotBypassLimit()throws Exception {
        var source=new MockHttpServletRequest("POST","/api/v1/projects");
        source.setContentType("application/json");source.setContent(new byte[262145]);
        var request=new HttpServletRequestWrapper(source){@Override public long getContentLengthLong(){return -1;}};
        var response=new MockHttpServletResponse();var executed=new AtomicBoolean();
        new RequestSecurityFilter().doFilter(request,response,(req,res)->executed.set(true));
        assertThat(response.getStatus()).isEqualTo(413);assertThat(executed).isFalse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
    @Test void boundedBodyRemainsReadableForController()throws Exception {
        var source=new MockHttpServletRequest("POST","/api/v1/projects");
        String content="{\"name\":\"Synthetic project\"}";
        source.setContentType("application/json");source.setContent(content.getBytes(StandardCharsets.UTF_8));
        var response=new MockHttpServletResponse();var executed=new AtomicBoolean();
        new RequestSecurityFilter().doFilter(source,response,(req,res)->{
            assertThat(new String(req.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo(content);
            executed.set(true);
        });
        assertThat(executed).isTrue();
    }
}

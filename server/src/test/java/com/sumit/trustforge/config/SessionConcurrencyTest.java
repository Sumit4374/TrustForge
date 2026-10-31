package com.sumit.trustforge.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import static org.assertj.core.api.Assertions.*;

class SessionConcurrencyTest {
    @Test void configuredStrategyRejectsEleventhSessionAndPermitsAfterExpiry()throws Exception {
        var registry=new SessionRegistryImpl();
        String principal="synthetic-user";
        for(int n=0;n<10;n++)registry.registerNewSession("session-"+n,principal);
        var strategy=new SecurityConfig().loginSessionStrategy(registry);
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThatThrownBy(()->strategy.onAuthentication(auth,new MockHttpServletRequest(),new MockHttpServletResponse()))
            .isInstanceOf(SessionAuthenticationException.class);
        registry.getSessionInformation("session-0").expireNow();
        strategy.onAuthentication(auth,new MockHttpServletRequest(),new MockHttpServletResponse());
        assertThat(registry.getAllSessions(principal,false)).hasSize(10);
    }
}

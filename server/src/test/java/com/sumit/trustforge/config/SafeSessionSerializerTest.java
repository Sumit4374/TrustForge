package com.sumit.trustforge.config;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import static org.assertj.core.api.Assertions.*;

class SafeSessionSerializerTest {
    SafeSessionSerializer serializer=new SafeSessionSerializer();
    @Test void serializesOnlyExplicitSafeTypes(){
        var auth=UsernamePasswordAuthenticationToken.authenticated(UUID.randomUUID().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext result=(SecurityContext)serializer.deserialize(serializer.serialize(new SecurityContextImpl(auth)));
        assertThat(result.getAuthentication().getName()).isEqualTo(auth.getName());
        assertThat(result.getAuthentication().getCredentials()).isNull();
        var token=new DefaultCsrfToken("X-CSRF-TOKEN","_csrf","random-value");
        assertThat(((DefaultCsrfToken)serializer.deserialize(serializer.serialize(token))).getToken()).isEqualTo("random-value");
        assertThatThrownBy(()->serializer.serialize(new java.util.Date())).hasMessageContaining("Unsupported");
        assertThatThrownBy(()->serializer.deserialize("{\"type\":\"java.lang.Runtime\"}".getBytes())).hasMessageContaining("decoding");
    }
    @Test void indexedSessionEventMetadataAndExpiryRoundTripSafely(){
        var codec=new SafeSessionSerializer();
        var metadata=java.util.Map.of("creationTime",123L,"maxInactiveInterval",1800,"sessionAttr:expired",true,"sessionAttr:generation",1);
        assertThat(codec.deserialize(codec.serialize(metadata))).isEqualTo(metadata);
        assertThat(codec.deserialize(codec.serialize(Boolean.TRUE))).isEqualTo(true);
        assertThatThrownBy(()->codec.serialize(java.util.Map.of("unsafe",new Object()))).isInstanceOf(org.springframework.data.redis.serializer.SerializationException.class);
    }
}

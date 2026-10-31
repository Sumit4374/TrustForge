package com.sumit.trustforge.identity;

import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.sumit.trustforge.common.ApiException;

@Service
public class RateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> INCREMENT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class);
    public RateLimiter(StringRedisTemplate redis){this.redis=redis;}
    public void check(String scope,String subject,int limit,int seconds){
        try {
            Long count=redis.execute(INCREMENT,Collections.singletonList("tf:rate-limit:"+scope+":"+IdentityService.hash(subject)),String.valueOf(seconds));
            if(count==null)throw new IllegalStateException();
            if(count>limit)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Please try again later.");
        }catch(ApiException e){throw e;}catch(RuntimeException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Authentication service temporarily unavailable");}
    }
}

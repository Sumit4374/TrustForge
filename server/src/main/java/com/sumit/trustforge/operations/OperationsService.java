package com.sumit.trustforge.operations;

import com.sumit.trustforge.ai.AIModelGateway;
import com.sumit.trustforge.common.ApiException;
import com.sumit.trustforge.infrastructure.ReadCache;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class OperationsService {
    private final JdbcTemplate db;
    private final AIModelGateway provider;
    private final ReadCache cache;
    public OperationsService(JdbcTemplate db, AIModelGateway provider, ReadCache cache) {
        this.db=db; this.provider=provider; this.cache=cache;
    }
    public Map<String,Object> health() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new ApiException(HttpStatus.FORBIDDEN,"Administrator access is required");
        var queue=db.queryForMap("""
            SELECT count(*) FILTER(WHERE state='QUEUED') AS queued,
              count(*) FILTER(WHERE state='RUNNING') AS running,
              count(*) FILTER(WHERE state='FAILED' AND finished_at>now()-interval '24 hours') AS failed_last_24_hours
            FROM evaluation_runs
            """);
        return Map.of("database","UP","cache",cache.healthy()?"UP":"UNAVAILABLE",
            "providerConfigured",provider.configured(),"jobs",queue,
            "workerConcurrency",2,"maximumQueueDepth",100,
            "deploymentMode","single worker-owning API instance");
    }
}

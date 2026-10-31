package com.sumit.trustforge.config;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Encloses Spring Session itself: failures before MVC must not become generic container errors. */
@Component @Order(SessionRepositoryFilter.DEFAULT_ORDER-1)
public class SessionInfrastructureFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        try {chain.doFilter(request,response);}
        catch(RuntimeException|ServletException failure) {
            if(!redisFailure(failure)||response.isCommitted())throw failure;
            unavailable(response);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("required_infrastructure_unavailable exception_type={}",failure.getClass().getSimpleName());
        }
    }
    public static boolean redisFailure(Throwable failure) {
        for(int depth=0;failure!=null&&depth<12;depth++,failure=failure.getCause()) {
            if(failure instanceof java.sql.SQLException sql){String state=sql.getSQLState();if(state!=null&&(state.startsWith("08")||java.util.Set.of("57P01","57P02","57P03").contains(state)))return true;}
            String name=failure.getClass().getName();
            if(name.startsWith("org.springframework.data.redis.")||name.startsWith("io.lettuce.core.")||failure instanceof org.springframework.dao.QueryTimeoutException||failure instanceof org.springframework.dao.DataAccessResourceFailureException||failure instanceof org.springframework.transaction.CannotCreateTransactionException)return true;
        }
        return false;
    }
    public static void unavailable(HttpServletResponse response)throws IOException {
        response.resetBuffer();response.setStatus(503);response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Robots-Tag","noindex, nofollow");response.setHeader("Retry-After","3");
        if(response.getHeader("X-Request-ID")==null)response.setHeader("X-Request-ID",UUID.randomUUID().toString());
        response.getWriter().write("{\"status\":503,\"title\":\"Required service unavailable\",\"detail\":\"Required secure infrastructure is temporarily unavailable. Please retry.\"}");
    }
}

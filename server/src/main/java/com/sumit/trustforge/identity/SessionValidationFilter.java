package com.sumit.trustforge.identity;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionValidationFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public SessionValidationFilter(UserRepository users){this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a!=null&&a.isAuthenticated()&&!"anonymousUser".equals(a.getName())){
            var session=req.getSession(false);boolean valid=false;
            if(session!=null)try{
                var u=users.findById(UUID.fromString(a.getName()));
                Object generation=session.getAttribute("generation");Object since=session.getAttribute("authenticatedAt");
                valid=u.isPresent()&&u.get().verified&&generation instanceof Integer&&u.get().sessionGeneration==(Integer)generation&&since instanceof Long&&Instant.now().toEpochMilli()-(Long)since<43_200_000;
            }catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException unavailable){
                // Identity cannot be checked. Keep authentication closed and preserve the session for recovery.
                com.sumit.trustforge.config.SessionInfrastructureFilter.unavailable(res);return;
            }catch(IllegalArgumentException e){valid=false;}
            if(!valid){SecurityContextHolder.clearContext();if(session!=null)session.invalidate();res.setStatus(401);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":401,\"title\":\"Session expired\",\"detail\":\"Sign in to continue\"}");return;}
        }
        chain.doFilter(req,res);
    }
}

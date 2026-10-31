package com.sumit.trustforge.identity;

import java.time.Instant;
import java.util.List;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Session establishment is serialized per account before enforcing concurrency limits. */
@Service
public class SessionLoginService {
    private final IdentityService identity;
    private final JdbcTemplate db;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final AuditService audit;
    public SessionLoginService(IdentityService identity,JdbcTemplate db,SessionAuthenticationStrategy sessions,
                               SecurityContextRepository contexts,CsrfTokenRepository csrf,AuditService audit) {
        this.identity=identity;this.db=db;this.sessions=sessions;this.contexts=contexts;this.csrf=csrf;this.audit=audit;
    }
    @Transactional public IdentityService.UserDto login(String email,String password,HttpServletRequest req,HttpServletResponse res) {
        UserAccount user=identity.authenticate(email,password);
        Integer generation=db.queryForObject("select session_generation from users where id=? and verified=true for update",Integer.class,user.id);
        if(generation==null||generation!=user.sessionGeneration)throw new ApiException(HttpStatus.UNAUTHORIZED,"Sign-in failed. Check your credentials and verify your email.");
        var auth=UsernamePasswordAuthenticationToken.authenticated(user.id.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role)));
        sessions.onAuthentication(auth,req,res);
        var session=req.getSession(true);session.setAttribute("generation",user.sessionGeneration);session.setAttribute("authenticatedAt",Instant.now().toEpochMilli());
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);csrf.saveToken(null,req,res);
        // Immediate Redis flush makes the indexed principal visible before releasing the account lock.
        audit.record(user.id,"ACCOUNT_LOGIN",user.id,"SUCCESS");
        return IdentityService.dto(user);
    }
}

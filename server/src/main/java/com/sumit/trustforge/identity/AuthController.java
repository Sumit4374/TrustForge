package com.sumit.trustforge.identity;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    record Register(@Email @NotBlank @Size(max=254)String email,@NotBlank @Size(max=100)String name,@Size(min=12,max=128) @NotNull String password){@Override public String toString(){return "Credential request [REDACTED]";}}
    record Profile(@NotBlank @Size(max=100)String name){}
    record Login(@Email @NotBlank @Size(max=254)String email,@NotNull @Size(max=128)String password){@Override public String toString(){return "Credential request [REDACTED]";}}
    record EmailRequest(@Email @NotBlank @Size(max=254)String email){}
    record Token(@NotBlank @Size(max=128)String token){@Override public String toString(){return "Token request [REDACTED]";}}
    record Reset(@NotBlank @Size(max=128)String token,@NotNull @Size(min=12,max=128)String password){@Override public String toString(){return "Credential request [REDACTED]";}}
    record Change(@NotNull @Size(max=128)String currentPassword,@NotNull @Size(min=12,max=128)String password){@Override public String toString(){return "Credential request [REDACTED]";}}
    private final IdentityService identity;private final RateLimiter rate;
    private final CsrfTokenRepository csrf;private final SessionLoginService loginSessions;
    public AuthController(IdentityService identity,RateLimiter rate,CsrfTokenRepository csrf,SessionLoginService loginSessions){this.identity=identity;this.rate=rate;this.csrf=csrf;this.loginSessions=loginSessions;}
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/me") IdentityService.UserDto me(){return IdentityService.dto(identity.current());}
    @PatchMapping("/me") IdentityService.UserDto profile(@Valid @RequestBody Profile body){return identity.updateName(body.name);}
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED) Map<String,String> resend(@Valid @RequestBody EmailRequest email,HttpServletRequest req){limit(req,"verification-resend",5);rate.check("verification-email",IdentityService.normalize(email.email),5,900);identity.resendVerification(email.email);return generic();}
    @PostMapping("/register") @ResponseStatus(HttpStatus.ACCEPTED) Map<String,String> register(@Valid @RequestBody Register body,HttpServletRequest req){limit(req,"register",5);identity.register(body.email,body.name,body.password);return generic();}
    @PostMapping("/login") IdentityService.UserDto login(@Valid @RequestBody Login body,HttpServletRequest req,HttpServletResponse res){
        limit(req,"login",10);rate.check("login-account",IdentityService.normalize(body.email),10,900);
        return loginSessions.login(body.email,body.password,req,res);
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest req,HttpServletResponse res){var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();csrf.saveToken(null,req,res);}
    @PostMapping("/verify") Map<String,String> verify(@Valid @RequestBody Token token,HttpServletRequest req){limit(req,"verify",15);identity.verify(token.token);return Map.of("message","Email verified. You can sign in.");}
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED) Map<String,String> forgot(@Valid @RequestBody EmailRequest email,HttpServletRequest req){limit(req,"reset-request",5);identity.requestReset(email.email);return generic();}
    @PostMapping("/reset-password") Map<String,String> reset(@Valid @RequestBody Reset body,HttpServletRequest req){limit(req,"reset",10);identity.reset(body.token,body.password);return Map.of("message","Password updated. Sign in again.");}
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT) void change(@Valid @RequestBody Change body,HttpServletRequest req,HttpServletResponse res){limit(req,"change-password",5);identity.changePassword(body.currentPassword,body.password);logout(req,res);}
    private void limit(HttpServletRequest req,String scope,int limit){rate.check(scope,req.getRemoteAddr(),limit,900);}
    private Map<String,String> generic(){return Map.of("message","If the address is eligible, an email will arrive shortly. Check your inbox.");}
}

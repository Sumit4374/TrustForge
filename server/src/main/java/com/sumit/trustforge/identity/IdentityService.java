package com.sumit.trustforge.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    public record UserDto(UUID id,String email,String name,String role) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final AuditService audit;
    private final SessionManagementService sessions;
    private final String publicUrl,from;
    private final String dummyHash;
    public IdentityService(UserRepository users,PasswordEncoder passwords,JdbcTemplate jdbc,JavaMailSender mail,AuditService audit,SessionManagementService sessions,
                           @Value("${trustforge.public-url}")String publicUrl,@Value("${trustforge.mail.from}")String from){
        this.users=users;this.passwords=passwords;this.jdbc=jdbc;this.mail=mail;this.audit=audit;this.sessions=sessions;this.publicUrl=publicUrl;this.from=from;
        dummyHash=passwords.encode("unavailable-account-comparison-"+UUID.randomUUID());
    }
    public static String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static String normalize(String email){return email.strip().toLowerCase(java.util.Locale.ROOT);}
    public static UserDto dto(UserAccount u){return new UserDto(u.id,u.email,u.name,u.role);}
    public UserAccount current(){return users.findById(Actor.require()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid session"));}
    @Transactional public void register(String email,String name,String password){
        ensureMailAvailable();
        String normalized=normalize(email);
        if(users.findByEmail(normalized).isPresent()){passwords.encode(password);return;}
        UserAccount user=users.saveAndFlush(new UserAccount(normalized,name.strip(),passwords.encode(password)));
        sendToken(user,false);audit.record(user.id,"ACCOUNT_REGISTER",user.id,"ACCEPTED");
    }
    @Transactional public UserDto updateName(String name){UserAccount user=current();user.name=name.strip();users.save(user);audit.record(user.id,"PROFILE_UPDATE",user.id,"SUCCESS");return dto(user);}
    @Transactional public void resendVerification(String email){ensureMailAvailable();users.findByEmail(normalize(email)).filter(u->!u.verified).ifPresent(u->{sendToken(u,false);audit.record(u.id,"VERIFICATION_REQUEST",u.id,"ACCEPTED");});}
    public UserAccount authenticate(String email,String password){
        var account=users.findByEmail(normalize(email));
        String stored=account.map(u->u.passwordHash).orElse(dummyHash);
        boolean match=passwords.matches(password,stored);
        if(!match||account.isEmpty()||!account.get().verified)throw new ApiException(HttpStatus.UNAUTHORIZED,"Sign-in failed. Check your credentials and verify your email.");
        return account.get();
    }
    @Transactional public void requestReset(String email){ensureMailAvailable();users.findByEmail(normalize(email)).filter(u->u.verified).ifPresent(u->sendToken(u,true));}
    private void ensureMailAvailable(){
        // Probe delivery configuration before account lookup so SMTP outages do not reveal existence.
        if(mail instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp)try{smtp.testConnection();}
        catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery is temporarily unavailable. Please try again later.");}
    }
    private void sendToken(UserAccount user,boolean reset){
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String table=reset?"password_reset_tokens":"user_verification_tokens";
        jdbc.update("delete from "+table+" where user_id=?",user.id);
        jdbc.update("insert into "+table+" (id,user_id,token_hash,expires_at) values (?,?,?,?)",UUID.randomUUID(),user.id,hash(token),Timestamp.from(Instant.now().plusSeconds(reset?1800:86400)));
        SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(user.email);
        message.setSubject(reset?"Reset your TrustForge password":"Verify your TrustForge email");
        message.setText((reset?"Reset your password":"Verify your email")+" using this single-use link:\n"+publicUrl+(reset?"/reset-password":"/verify-email")+"#token="+token+"\nIf you did not request this, ignore this email.");
        try{mail.send(message);}catch(RuntimeException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery is temporarily unavailable. Please try again later.");}
    }
    @Transactional public void verify(String token){UUID id=consume(token,false);UserAccount u=users.findById(id).orElseThrow(ApiException::notFound);u.verified=true;users.save(u);audit.record(id,"EMAIL_VERIFY",id,"SUCCESS");}
    @Transactional public void reset(String token,String password){UUID id=consume(token,true);UserAccount u=users.findById(id).orElseThrow(ApiException::notFound);u.passwordHash=passwords.encode(password);u.sessionGeneration++;users.saveAndFlush(u);sessions.revokeAll(id);audit.record(id,"PASSWORD_RESET",id,"SUCCESS");}
    private UUID consume(String token,boolean reset){
        if(token==null||token.length()<32||token.length()>128)throw ApiException.invalid("Invalid or expired token");
        String table=reset?"password_reset_tokens":"user_verification_tokens";
        var found=jdbc.query("select id,user_id from "+table+" where token_hash=? and consumed_at is null and expires_at>now() for update",(r,n)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)},hash(token));
        if(found.size()!=1)throw ApiException.invalid("Invalid or expired token");
        jdbc.update("update "+table+" set consumed_at=now() where id=?",found.getFirst()[0]);return found.getFirst()[1];
    }
    @Transactional public void changePassword(String current,String password){
        UserAccount u=current();if(!passwords.matches(current,u.passwordHash))throw ApiException.invalid("Current password is incorrect");
        u.passwordHash=passwords.encode(password);u.sessionGeneration++;users.saveAndFlush(u);sessions.revokeAll(u.id);
        jdbc.update("update password_reset_tokens set consumed_at=now() where user_id=? and consumed_at is null",u.id);
        audit.record(u.id,"PASSWORD_CHANGE",u.id,"SUCCESS");
    }
}

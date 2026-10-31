package com.sumit.trustforge.identity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.common.ApiException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Owner-scoped session metadata; handles never expose usable cookie values. */
@Service
public class SessionManagementService {
    public record SessionDto(String handle, Instant signedInAt, Instant lastAccessedAt, Instant expiresAt, boolean current) {}
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final UserRepository users;
    private final AuditService audit;

    public SessionManagementService(FindByIndexNameSessionRepository<? extends Session> sessions, UserRepository users, AuditService audit) {
        this.sessions=sessions;this.users=users;this.audit=audit;
    }

    public List<SessionDto> list(String currentId) {
        UUID owner=Actor.require();
        UserAccount user=users.findById(owner).orElseThrow(ApiException::notFound);
        return sessions.findByPrincipalName(owner.toString()).values().stream()
            .filter(s->valid(s,user.sessionGeneration))
            .map(s->{
                Instant signedIn=Instant.ofEpochMilli((Long)s.getAttribute("authenticatedAt"));
                Instant idle=s.getLastAccessedTime().plus(s.getMaxInactiveInterval());
                Instant absolute=signedIn.plusSeconds(43_200);
                return new SessionDto(IdentityService.hash(s.getId()),signedIn,s.getLastAccessedTime(),
                    idle.isBefore(absolute)?idle:absolute,s.getId().equals(currentId));
            })
            .sorted(Comparator.comparing(SessionDto::current).reversed().thenComparing(SessionDto::lastAccessedAt,Comparator.reverseOrder()))
            .limit(10).toList();
    }

    public void revoke(String handle,String currentId) {
        UUID owner=Actor.require();
        if(!handle.matches("[a-f0-9]{64}"))throw ApiException.invalid("Invalid session reference");
        var found=sessions.findByPrincipalName(owner.toString()).values().stream()
            .filter(s->IdentityService.hash(s.getId()).equals(handle)).findFirst().orElseThrow(ApiException::notFound);
        if(found.getId().equals(currentId))throw ApiException.invalid("Use sign out to end your current session.");
        sessions.deleteById(found.getId());
        audit.record(owner,"SESSION_REVOKE",owner,"SUCCESS");
    }

    void revokeAll(UUID owner) {
        // Generation revocation remains the durable check; remove old indexes to free login slots.
        sessions.findByPrincipalName(owner.toString()).keySet().forEach(sessions::deleteById);
    }

    private boolean valid(Session session,int generation) {
        Object stored=session.getAttribute("generation"),since=session.getAttribute("authenticatedAt");
        return !session.isExpired() && stored instanceof Integer && (Integer)stored==generation
            && since instanceof Long && Instant.now().toEpochMilli()-(Long)since<43_200_000;
    }
}

package com.sumit.trustforge.identity;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth/sessions")
public class SessionController {
    private final SessionManagementService sessions;
    public SessionController(SessionManagementService sessions){this.sessions=sessions;}
    @GetMapping List<SessionManagementService.SessionDto> list(HttpServletRequest req){return sessions.list(req.getSession().getId());}
    @DeleteMapping("/{handle}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String handle,HttpServletRequest req){sessions.revoke(handle,req.getSession().getId());}
}

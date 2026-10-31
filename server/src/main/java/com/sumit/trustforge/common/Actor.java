package com.sumit.trustforge.common;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Actor {
    private Actor() {}
    public static UUID require() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        try { return UUID.fromString(a.getName()); }
        catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session"); }
    }
}

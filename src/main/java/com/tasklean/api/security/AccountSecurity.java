package com.tasklean.api.security;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Account-level authorization checks referenced from {@code @PreAuthorize} expressions
 * (as {@code @accountSecurity}). Lets a user act on their own account without a platform role.
 */
@Component("accountSecurity")
public class AccountSecurity {

    /**
     * Whether the current caller is the user identified by the given public UID.
     *
     * @param uid the target user's public UID
     * @return {@code true} if an authenticated caller's UID matches, {@code false} otherwise
     */
    public boolean isSelf(String uid) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof AuthPrincipal principal
                && principal.uid() != null
                && principal.uid().equals(uid);
    }
}

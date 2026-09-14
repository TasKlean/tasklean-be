package com.tasklean.api.auth.jwt;

import com.tasklean.api.domain.user.UserRole;

/**
 * The authenticated caller, built purely from validated JWT claims — no database load.
 * Set as the security principal by {@link JwtAuthenticationFilter} on every authenticated request.
 */
public record AuthPrincipal(Long userId, String uid, String email, UserRole role) {
}

-- Add platform-level role to the user for role-based access control (RBAC).
-- SUPER_ADMIN: full platform access, including granting/revoking roles.
-- ADMIN: read/support access across the platform; no destructive or role-granting actions.
-- USER: standard account (default) — limited to own data and joined groups.
ALTER TABLE "user"
    ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER'));

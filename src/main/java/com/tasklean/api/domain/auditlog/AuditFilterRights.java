package com.tasklean.api.domain.auditlog;

/**
 * What a caller may narrow a feed by, as opposed to which rows they may read. Decided by
 * {@link AuditLogVisibility} alongside the visibility predicate - both answers come off the same
 * group-role lookup - and consumed by {@link AuditLogFilter}.
 *
 * @param unrestricted whether the caller may filter by anybody's actions
 * @param callerUid    the caller's own user uid, the one actor a restricted caller may name
 */
record AuditFilterRights(boolean unrestricted, String callerUid) {

    /**
     * Rights for a caller who may filter by any actor - a group admin, a platform role, or anyone
     * reading their own account history, where every row is already theirs.
     *
     * @return the unrestricted rights
     */
    static AuditFilterRights anyActor() {
        return new AuditFilterRights(true, null);
    }

    /**
     * Rights for a caller who may filter only by their own actions.
     *
     * @param callerUid the caller's user uid
     * @return the restricted rights
     */
    static AuditFilterRights ownActionsOnly(String callerUid) {
        return new AuditFilterRights(false, callerUid);
    }

    /**
     * Whether this caller may ask for one actor's entries.
     *
     * @param actorRef the user uid the caller named
     * @return true if the filter is theirs to ask for
     */
    boolean mayFilterByActor(String actorRef) {
        return unrestricted || actorRef.equals(callerUid);
    }
}

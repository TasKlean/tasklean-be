package com.tasklean.api.common.email;

/**
 * How much a flow depends on an email actually going out, which decides what happens when the mail
 * provider refuses. Chosen per message rather than per provider: the same outage must not be allowed
 * to break an operation that has already succeeded.
 */
public enum Delivery {

    /**
     * The email is the deliverable, so a failure fails the request. Only safe where every branch of
     * the endpoint sends something — otherwise the error itself reveals which branch ran.
     */
    ESSENTIAL,

    /**
     * The operation already succeeded and the email only informs the owner, so a failure is logged
     * and swallowed. Also the right choice for an email the caller must not be able to distinguish
     * from no email at all.
     */
    ADVISORY
}

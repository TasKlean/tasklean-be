package com.tasklean.api.security.ratelimit;

import io.github.bucket4j.Bucket;

/**
 * Supplies the rate-limit token bucket for a given key and tier. Abstracts the backing store so
 * the in-memory (Caffeine) implementation can be swapped for a distributed one (e.g. Redis) if the
 * app ever runs more than one instance.
 */
public interface BucketRegistry {

    /**
     * Returns the bucket for a key, creating it from the tier on first use. The key must be unique
     * per limit dimension (e.g. {@code "auth:1.2.3.4"}, {@code "apiUser:42"}).
     *
     * @param key  the unique bucket key
     * @param tier the tier defining the bucket's capacity and refill
     * @return the token bucket for that key
     */
    Bucket resolve(String key, RateLimitProperties.Tier tier);
}

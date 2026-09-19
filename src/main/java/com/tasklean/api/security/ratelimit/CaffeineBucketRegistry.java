package com.tasklean.api.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * In-memory {@link BucketRegistry} backed by a Caffeine cache of Bucket4j buckets - one per key.
 * Suitable for a single instance; buckets live only in this process. Idle buckets are evicted to
 * bound memory, and the cache is capped in size.
 */
@Component
public class CaffeineBucketRegistry implements BucketRegistry {

    // Eviction after 2h idle keeps memory bounded. It must exceed the longest configured refill
    // period (register = 1h): once a bucket has fully refilled, evicting it and re-creating a fresh
    // full one is identical.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    @Override
    public Bucket resolve(String key, RateLimitProperties.Tier tier) {
        return buckets.get(key, k -> newBucket(tier));
    }

    private Bucket newBucket(RateLimitProperties.Tier tier) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(tier.getCapacity())
                .refillGreedy(tier.getCapacity(), tier.getRefillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}

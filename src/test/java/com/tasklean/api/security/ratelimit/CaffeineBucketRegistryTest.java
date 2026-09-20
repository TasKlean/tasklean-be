package com.tasklean.api.security.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineBucketRegistryTest {

    private final CaffeineBucketRegistry registry = new CaffeineBucketRegistry();

    private RateLimitProperties.Tier tier(int capacity) {
        return new RateLimitProperties.Tier(capacity, Duration.ofMinutes(1));
    }

    @Test
    void resolve_sameKey_returnsSameBucket() {
        Bucket first = registry.resolve("k", tier(5));
        Bucket second = registry.resolve("k", tier(5));

        assertThat(first).isSameAs(second);
    }

    @Test
    void resolve_differentKeys_returnDifferentBuckets() {
        assertThat(registry.resolve("a", tier(5))).isNotSameAs(registry.resolve("b", tier(5)));
    }

    @Test
    void resolve_bucketEnforcesTierCapacity() {
        Bucket bucket = registry.resolve("k", tier(2));

        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();
    }
}

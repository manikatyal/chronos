package com.mihir.jobscheduler.security;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long currentCount,
        long retryAfterSeconds) {

    public long remainingRequests() {
        return Math.max(0, limit - currentCount);
    }
}

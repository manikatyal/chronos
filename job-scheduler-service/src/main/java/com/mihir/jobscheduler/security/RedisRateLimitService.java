package com.mihir.jobscheduler.security;

import com.mihir.jobscheduler.config.ChronosRateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimitService {

    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {current, ttl}
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final ChronosRateLimitProperties rateLimitProperties;

    public RateLimitDecision checkLimit(String clientKey) {
        String redisKey = "chronos:rate-limit:" + clientKey;
        DefaultRedisScript<List> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, List.class);

        try {
            List<?> result = stringRedisTemplate.execute(
                    script,
                    List.of(redisKey),
                    String.valueOf(rateLimitProperties.requestsPerMinute()),
                    String.valueOf(rateLimitProperties.windowSeconds()));

            if (result == null || result.size() < 2) {
                return allowAll();
            }

            long currentCount = toLong(result.get(0));
            long ttlSeconds = Math.max(0, toLong(result.get(1)));
            return new RateLimitDecision(
                    currentCount <= rateLimitProperties.requestsPerMinute(),
                    rateLimitProperties.requestsPerMinute(),
                    currentCount,
                    ttlSeconds);
        } catch (DataAccessException exception) {
            log.warn("Redis rate limiting skipped for key {} due to error: {}", clientKey, exception.getMessage());
            return allowAll();
        }
    }

    private RateLimitDecision allowAll() {
        return new RateLimitDecision(true, rateLimitProperties.requestsPerMinute(), 0, 0);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}

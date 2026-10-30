package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.exception.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class LoginRateLimiter {

    private final ConcurrentMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final int limit;
    private final long windowSeconds;
    private final boolean redisEnabled;
    private final String redisKeyPrefix = "nekoauth:login-attempts:";

    public LoginRateLimiter(AuthProperties properties, StringRedisTemplate redisTemplate,
                            Environment environment) {
        this.limit = Math.max(1, properties.rateLimit().attempts());
        this.windowSeconds = Math.max(1, properties.rateLimit().window().toSeconds());
        this.redisTemplate = redisTemplate;
        this.redisEnabled = StringUtils.hasText(environment.getProperty("spring.data.redis.host"));
    }

    public void check(String clientKey) {
        if (redisEnabled) {
            checkRedis(clientKey);
            return;
        }
        AttemptWindow window = attempts.get(clientKey);
        if (window != null && window.expiresAt().isAfter(Instant.now()) && window.failures() >= limit) {
            throw ApiException.tooManyRequests("TOO_MANY_ATTEMPTS", "登录尝试过于频繁，请稍后再试");
        }
    }

    public void recordFailure(String clientKey) {
        if (redisEnabled) {
            recordRedisFailure(clientKey);
            return;
        }
        Instant now = Instant.now();
        attempts.compute(clientKey, (key, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new AttemptWindow(1, now.plusSeconds(windowSeconds));
            }
            return new AttemptWindow(current.failures() + 1, current.expiresAt());
        });
    }

    public void recordSuccess(String clientKey) {
        if (redisEnabled) {
            redisTemplate.delete(redisKeyPrefix + clientKey);
            return;
        }
        attempts.remove(clientKey);
    }

    @Scheduled(fixedDelay = 600_000)
    void removeExpiredWindows() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record AttemptWindow(int failures, Instant expiresAt) {
    }

    private void checkRedis(String clientKey) {
        String value = redisTemplate.opsForValue().get(redisKeyPrefix + clientKey);
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            if (Long.parseLong(value) >= limit) {
                throw ApiException.tooManyRequests("TOO_MANY_ATTEMPTS", "登录尝试过于频繁，请稍后再试");
            }
        } catch (NumberFormatException exception) {
            redisTemplate.delete(redisKeyPrefix + clientKey);
        }
    }

    private void recordRedisFailure(String clientKey) {
        String key = redisKeyPrefix + clientKey;
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
    }
}

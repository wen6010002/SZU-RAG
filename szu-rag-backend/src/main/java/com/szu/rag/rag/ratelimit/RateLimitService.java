package com.szu.rag.rag.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public boolean allowRequest(String key, int maxRequests, int windowSeconds) {
        String redisKey = "rate_limit:" + key;
        String count = redisTemplate.opsForValue().get(redisKey);

        if (count == null) {
            redisTemplate.opsForValue().set(redisKey, "1", windowSeconds, TimeUnit.SECONDS);
            return true;
        }

        int current = Integer.parseInt(count);
        if (current >= maxRequests) {
            return false;
        }

        redisTemplate.opsForValue().increment(redisKey);
        return true;
    }
}

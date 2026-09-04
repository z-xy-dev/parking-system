package com.parking.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(UserFeignFallbackFactory.class);

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("user-service 熔断降级触发, 原因: {}", cause.getMessage());
        return id -> {
            log.warn("降级: getUserProfile 返回空, userId={}", id);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("degraded", true);
            return fallback;
        };
    }
}

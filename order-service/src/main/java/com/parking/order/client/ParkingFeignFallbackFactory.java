package com.parking.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParkingFeignFallbackFactory implements FallbackFactory<ParkingFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(ParkingFeignFallbackFactory.class);

    @Override
    public ParkingFeignClient create(Throwable cause) {
        log.error("parking-service 熔断降级触发, 原因: {}", cause.getMessage());
        return new ParkingFeignClient() {
            @Override
            public void decrementSpot(Long id) {
                log.warn("降级: decrementSpot 跳过, spaceId={}", id);
            }

            @Override
            public void incrementSpot(Long id) {
                log.warn("降级: incrementSpot 跳过, spaceId={}", id);
            }

            @Override
            public Map<String, Object> getSpaceDetail(Long id) {
                log.warn("降级: getSpaceDetail 返回空, spaceId={}", id);
                return Collections.emptyMap();
            }

            @Override
            public List<Map<String, Object>> getOwnerSpaces(Long userId) {
                log.warn("降级: getOwnerSpaces 返回空, userId={}", userId);
                return Collections.emptyList();
            }

            @Override
            public List<Map<String, Object>> getSpots(Long spaceId) {
                log.warn("降级: getSpots 返回空, spaceId={}", spaceId);
                return Collections.emptyList();
            }

            @Override
            public Map<String, Object> reserveSpot(Long spaceId, Integer spotNumber) {
                log.warn("降级: reserveSpot 返回失败, spaceId={}, spot={}", spaceId, spotNumber);
                return degradedResult(false, true);
            }

            @Override
            public Map<String, Object> releaseSpot(Long spaceId, Integer spotNumber) {
                log.warn("降级: releaseSpot 返回失败, spaceId={}, spot={}", spaceId, spotNumber);
                return degradedResult(false, true);
            }
        };
    }

    private static Map<String, Object> degradedResult(boolean success, boolean degraded) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("degraded", degraded);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "degraded");
        result.put("data", data);
        return result;
    }
}

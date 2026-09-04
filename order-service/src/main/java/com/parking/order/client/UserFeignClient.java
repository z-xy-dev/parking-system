package com.parking.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "user-service", fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 查询用户基本信息（脱敏）。该接口带 /internal/ 前缀，网关已禁止公网访问，
     * 仅允许服务间通过 Feign 直连调用。
     */
    @GetMapping("/api/user/internal/{id}")
    Map<String, Object> getUserProfile(@PathVariable("id") Long id);
}

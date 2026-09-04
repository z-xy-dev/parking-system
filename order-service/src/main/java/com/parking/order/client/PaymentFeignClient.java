package com.parking.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "payment-service", fallbackFactory = PaymentFeignFallbackFactory.class)
public interface PaymentFeignClient {

    /**
     * 扣款。payment-service 内部保证幂等：同一订单已有成功记录时直接返回原交易号，不会重复扣钱。
     *
     * @return 交易流水号；返回 {@code DEGRADED} 表示支付服务熔断降级
     */
    @PostMapping("/api/payment/internal/pay/{orderId}")
    String pay(@PathVariable("orderId") Long orderId,
               @RequestParam("userId") Long userId,
               @RequestParam("amount") BigDecimal amount);

    @GetMapping("/api/payment/internal/records/{orderId}")
    Map<String, Object> getRecords(@PathVariable("orderId") Long orderId);
}

package com.parking.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@Component
public class PaymentFeignFallbackFactory implements FallbackFactory<PaymentFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(PaymentFeignFallbackFactory.class);

    @Override
    public PaymentFeignClient create(Throwable cause) {
        log.error("payment-service 熔断降级触发, 原因: {}", cause.getMessage());
        return new PaymentFeignClient() {
            @Override
            public String pay(Long orderId, Long userId, BigDecimal amount) {
                log.warn("降级: pay 跳过, orderId={}, userId={}, amount={}, 订单先完成、支付待补偿", orderId, userId, amount);
                return "DEGRADED";
            }

            @Override
            public Map<String, Object> getRecords(Long orderId) {
                log.warn("降级: getRecords 返回空, orderId={}", orderId);
                return Collections.emptyMap();
            }
        };
    }
}

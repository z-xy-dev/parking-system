package com.parking.payment.service;

import com.parking.payment.entity.PaymentRecord;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {
    /**
     * 针对指定订单发起支付。
     *
     * @param orderId 订单ID
     * @param userId  支付用户ID
     * @param amount  支付金额，必须与订单金额一致
     * @return 交易流水号
     */
    String pay(Long orderId, Long userId, BigDecimal amount);

    List<PaymentRecord> getByOrderId(Long orderId);
}

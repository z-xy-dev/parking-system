package com.parking.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.parking.payment.entity.PaymentRecord;
import com.parking.payment.mapper.PaymentMapper;
import com.parking.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Override
    @Transactional
    public String pay(Long orderId, Long userId, BigDecimal amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("支付用户ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付金额必须大于0");
        }

        // 幂等：同一订单只要已有成功的支付记录，就不再重复扣款，直接返回原交易号。
        // 订单完成后统一结算，重复点击"完成"或网络重试都不会造成二次扣费。
        List<PaymentRecord> paid = paymentMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getStatus, "SUCCESS"));
        if (!paid.isEmpty()) {
            log.warn("订单 {} 已支付过，跳过重复扣款，tradeNo={}", orderId, paid.get(0).getTradeNo());
            return paid.get(0).getTradeNo();
        }

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setAmount(amount);
        record.setMethod("WECHAT");
        record.setTradeNo(IdUtil.getSnowflakeNextIdStr());
        record.setStatus("SUCCESS");
        record.setPaidAt(LocalDateTime.now());
        paymentMapper.insert(record);
        return record.getTradeNo();
    }

    @Override
    public List<PaymentRecord> getByOrderId(Long orderId) {
        return paymentMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId));
    }
}

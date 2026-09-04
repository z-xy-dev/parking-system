package com.parking.payment.service;

import com.parking.payment.entity.PaymentRecord;
import com.parking.payment.mapper.PaymentMapper;
import com.parking.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void testPay() {
        when(paymentMapper.insert(any(PaymentRecord.class))).thenReturn(1);

        String tradeNo = paymentService.pay(1L, 1L, new BigDecimal("30.00"));
        assertNotNull(tradeNo);
        verify(paymentMapper, times(1)).insert(argThat(r -> r.getOrderId().equals(1L)
                && r.getUserId().equals(1L)
                && r.getAmount().compareTo(new BigDecimal("30.00")) == 0));
    }

    @Test
    void testGetByOrderId() {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderId(1L);
        record.setTradeNo("TRADE123");
        when(paymentMapper.selectList(any())).thenReturn(List.of(record));

        List<PaymentRecord> result = paymentService.getByOrderId(1L);
        assertEquals(1, result.size());
        assertEquals("TRADE123", result.get(0).getTradeNo());
    }
}

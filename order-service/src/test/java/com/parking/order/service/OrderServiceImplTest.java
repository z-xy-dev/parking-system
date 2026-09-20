package com.parking.order.service;

import com.parking.common.exception.BizException;
import com.parking.order.client.ParkingFeignClient;
import com.parking.order.client.PaymentFeignClient;
import com.parking.order.entity.BookingOrder;
import com.parking.order.mapper.OrderMapper;
import com.parking.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ParkingFeignClient parkingFeignClient;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private BookingOrder testOrder;

    @BeforeEach
    void setUp() {
        // 基础设施降级路径：Redis 幂等键 setIfAbsent 返回 null（等效“键不存在，首次进入”）；
        // Redisson 抢锁返回 false（等效锁竞争失败，降级为纯 DB 原子流程）。
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(null);

        RLock rlock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rlock);
        try {
            when(rlock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // parking 服务返回：单价 10.00 / 占位成功 / 释放成功
        when(parkingFeignClient.getSpaceDetail(anyLong()))
                .thenReturn(Map.of("data", Map.of("pricePerHour", "10.00")));
        when(parkingFeignClient.reserveSpot(anyLong(), anyInt()))
                .thenReturn(Map.of("data", Map.of("success", true, "degraded", false)));
        when(parkingFeignClient.releaseSpot(anyLong(), anyInt()))
                .thenReturn(Map.of("data", Map.of("success", true)));

        testOrder = new BookingOrder();
        testOrder.setId(1L);
        testOrder.setUserId(1L);
        testOrder.setSpaceId(1L);
        testOrder.setSpotNumber("1");
        testOrder.setCarPlate("粤A12345");
        testOrder.setStartTime(LocalDateTime.now().plusHours(1));
        testOrder.setEndTime(LocalDateTime.now().plusHours(3));
        testOrder.setTotalAmount(new BigDecimal("30.00"));
        testOrder.setStatus("RESERVED");
    }

    @Test
    void testCreateSuccess() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.insert(any(BookingOrder.class))).thenReturn(1);
        when(paymentFeignClient.pay(anyLong(), anyLong(), any(BigDecimal.class))).thenReturn("TRADE123");

        BookingOrder result = orderService.create(testOrder);

        assertNotNull(result.getOrderNo());
        assertEquals("RESERVED", result.getStatus());
        assertEquals("UNPAID", result.getPayStatus());
        assertEquals(2, result.getHours());
        // 下单只占位、不落支付流水
        verify(parkingFeignClient, times(1)).reserveSpot(1L, 1);
        verify(orderMapper, times(1)).insert(testOrder);
        verify(paymentFeignClient, never()).pay(anyLong(), anyLong(), any(BigDecimal.class));
    }

    @Test
    void testCreateRejectsBlankSpot() {
        testOrder.setSpotNumber("");
        assertThrows(BizException.class, () -> orderService.create(testOrder));
    }

    @Test
    void testCancelSuccess() {
        testOrder.setStatus("RESERVED");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);
        when(orderMapper.updateById(any())).thenReturn(1);

        orderService.cancel(1L, 1L);

        assertEquals("CANCELED", testOrder.getStatus());
        verify(parkingFeignClient, times(1)).releaseSpot(1L, 1);
    }

    @Test
    void testCancelWrongUser() {
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.cancel(1L, 999L));
        verify(parkingFeignClient, never()).releaseSpot(anyLong(), anyInt());
    }

    @Test
    void testCancelWrongStatus() {
        testOrder.setStatus("USING");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.cancel(1L, 1L));
        verify(parkingFeignClient, never()).releaseSpot(anyLong(), anyInt());
    }

    @Test
    void testDetailFound() {
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        BookingOrder result = orderService.detail(1L);
        assertEquals("粤A12345", result.getCarPlate());
    }

    @Test
    void testDetailNotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> orderService.detail(999L));
    }

    @Test
    void testMyOrders() {
        when(orderMapper.selectList(any())).thenReturn(List.of(testOrder));

        List<BookingOrder> result = orderService.myOrders(1L);
        assertEquals(1, result.size());
    }

    @Test
    void testCompleteSuccess() {
        testOrder.setStatus("USING");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);
        when(orderMapper.updateById(any())).thenReturn(1);

        orderService.complete(1L, 1L);

        assertEquals("COMPLETED", testOrder.getStatus());
        verify(parkingFeignClient, times(1)).releaseSpot(1L, 1);
    }

    @Test
    void testStartUseSuccess() {
        testOrder.setStatus("RESERVED");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);
        when(orderMapper.updateById(any())).thenReturn(1);

        orderService.startUse(1L, 1L);

        assertEquals("USING", testOrder.getStatus());
        verify(orderMapper, times(1)).updateById(testOrder);
        // 开始使用不释放车位、不落支付流水
        verify(parkingFeignClient, never()).releaseSpot(anyLong(), anyInt());
    }

    @Test
    void testStartUseWrongUser() {
        testOrder.setStatus("RESERVED");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.startUse(1L, 999L));
        verify(orderMapper, never()).updateById(any());
    }

    @Test
    void testStartUseWrongStatus() {
        testOrder.setStatus("CANCELED");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.startUse(1L, 1L));
        verify(orderMapper, never()).updateById(any());
    }
}

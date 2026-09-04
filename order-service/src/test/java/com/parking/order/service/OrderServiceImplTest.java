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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ParkingFeignClient parkingFeignClient;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private BookingOrder testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new BookingOrder();
        testOrder.setId(1L);
        testOrder.setUserId(1L);
        testOrder.setSpaceId(1L);
        testOrder.setCarPlate("粤A12345");
        testOrder.setStartTime(LocalDateTime.now().plusHours(1));
        testOrder.setEndTime(LocalDateTime.now().plusHours(3));
        testOrder.setTotalAmount(new BigDecimal("30.00"));
        testOrder.setStatus("RESERVED");
    }

    @Test
    void testCreateSuccess() {
        doNothing().when(parkingFeignClient).decrementSpot(anyLong());
        when(orderMapper.insert(any(BookingOrder.class))).thenReturn(1);
        when(paymentFeignClient.pay(anyLong(), anyLong(), any(BigDecimal.class))).thenReturn("TRADE123");

        BookingOrder result = orderService.create(testOrder);

        assertNotNull(result.getOrderNo());
        assertEquals("RESERVED", result.getStatus());
        assertEquals(2, result.getHours());
        verify(parkingFeignClient, times(1)).decrementSpot(1L);
        verify(orderMapper, times(1)).insert(testOrder);
        verify(paymentFeignClient, times(1)).pay(1L, 1L, new BigDecimal("30.00"));
    }

    @Test
    void testCancelSuccess() {
        testOrder.setStatus("RESERVED");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);
        when(orderMapper.updateById(any())).thenReturn(1);

        orderService.cancel(1L, 1L);

        assertEquals("CANCELED", testOrder.getStatus());
        verify(parkingFeignClient, times(1)).incrementSpot(1L);
    }

    @Test
    void testCancelWrongUser() {
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.cancel(1L, 999L));
        verify(parkingFeignClient, never()).incrementSpot(anyLong());
    }

    @Test
    void testCancelWrongStatus() {
        testOrder.setStatus("USING");
        when(orderMapper.selectById(1L)).thenReturn(testOrder);

        assertThrows(BizException.class, () -> orderService.cancel(1L, 1L));
        verify(parkingFeignClient, never()).incrementSpot(anyLong());
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
        verify(parkingFeignClient, times(1)).incrementSpot(1L);
    }
}

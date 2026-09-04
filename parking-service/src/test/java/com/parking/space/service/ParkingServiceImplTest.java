package com.parking.space.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.parking.common.exception.BizException;
import com.parking.common.result.PageResult;
import com.parking.space.entity.ParkingSpace;
import com.parking.space.mapper.ParkingSpaceMapper;
import com.parking.space.service.ParkingSpotService;
import com.parking.space.service.impl.ParkingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
class ParkingServiceImplTest {

    @Mock
    private ParkingSpaceMapper parkingSpaceMapper;

    @Mock
    private ParkingSpotService parkingSpotService;

    @InjectMocks
    private ParkingServiceImpl parkingService;

    private ParkingSpace testSpace;

    @BeforeEach
    void setUp() {
        testSpace = new ParkingSpace();
        testSpace.setId(1L);
        testSpace.setOwnerId(1L);
        testSpace.setTitle("测试停车场");
        testSpace.setAddress("广州市天河区");
        testSpace.setPricePerHour(new BigDecimal("10.00"));
        testSpace.setTotalSpots(50);
        testSpace.setAvailableSpots(50);
        testSpace.setStatus("AVAILABLE");
        testSpace.setDescription("测试车位");
    }

    @Test
    void testPublish() {
        parkingService.publish(testSpace);
        assertEquals("AVAILABLE", testSpace.getStatus());
        assertEquals(50, testSpace.getAvailableSpots());
        verify(parkingSpaceMapper, times(1)).insert(testSpace);
    }

    @Test
    void testDetailNotFound() {
        when(parkingSpaceMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class, () -> parkingService.detail(999L));
    }

    @Test
    void testDetailFound() {
        when(parkingSpaceMapper.selectById(1L)).thenReturn(testSpace);
        ParkingSpace result = parkingService.detail(1L);
        assertEquals("测试停车场", result.getTitle());
    }

    @Test
    void testDecrementSpot() {
        when(parkingSpaceMapper.selectById(1L)).thenReturn(testSpace);
        parkingService.decrementSpot(1L);
        assertEquals(49, testSpace.getAvailableSpots());
        verify(parkingSpaceMapper, times(1)).updateById(testSpace);
    }

    @Test
    void testDecrementSpotFullException() {
        testSpace.setAvailableSpots(0);
        when(parkingSpaceMapper.selectById(1L)).thenReturn(testSpace);
        assertThrows(BizException.class, () -> parkingService.decrementSpot(1L));
    }

    @Test
    void testIncrementSpot() {
        testSpace.setAvailableSpots(49);
        testSpace.setStatus("FULL");
        when(parkingSpaceMapper.selectById(1L)).thenReturn(testSpace);
        parkingService.incrementSpot(1L);
        assertEquals(50, testSpace.getAvailableSpots());
        assertEquals("AVAILABLE", testSpace.getStatus());
    }

    @Test
    void testListEmpty() {
        Page<ParkingSpace> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(parkingSpaceMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<ParkingSpace> result = parkingService.list(1, 10, null);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }
}

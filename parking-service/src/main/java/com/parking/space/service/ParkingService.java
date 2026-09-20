package com.parking.space.service;

import com.parking.common.result.PageResult;
import com.parking.space.entity.ParkingSpace;
import java.util.List;
import java.util.Map;

public interface ParkingService {
    PageResult<ParkingSpace> list(Integer page, Integer size, String keyword);
    ParkingSpace detail(Long id);
    void publish(ParkingSpace space);
    void update(ParkingSpace space);
    void updateStatus(Long id, String status);
    List<Map<String, Object>> getOwnerSpaces(Long ownerId);
}

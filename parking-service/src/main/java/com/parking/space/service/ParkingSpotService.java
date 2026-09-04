package com.parking.space.service;

import com.parking.space.entity.ParkingSpot;
import java.util.List;

public interface ParkingSpotService {
    List<ParkingSpot> getSpotsBySpaceId(Long spaceId);
    boolean reserveSpot(Long spaceId, Integer spotNumber);
    boolean releaseSpot(Long spaceId, Integer spotNumber);
    void generateSpots(Long spaceId, Integer totalSpots);
    int getAvailableSpotCount(Long spaceId);
    void syncAvailableCount(Long spaceId);
}

package com.parking.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.parking.space.entity.ParkingSpace;
import com.parking.space.entity.ParkingSpot;
import com.parking.space.mapper.ParkingSpaceMapper;
import com.parking.space.mapper.ParkingSpotMapper;
import com.parking.space.service.ParkingSpotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ParkingSpotServiceImpl implements ParkingSpotService {

    private final ParkingSpotMapper parkingSpotMapper;
    private final ParkingSpaceMapper parkingSpaceMapper;

    public ParkingSpotServiceImpl(ParkingSpotMapper parkingSpotMapper,
                                  ParkingSpaceMapper parkingSpaceMapper) {
        this.parkingSpotMapper = parkingSpotMapper;
        this.parkingSpaceMapper = parkingSpaceMapper;
    }

    @Override
    public List<ParkingSpot> getSpotsBySpaceId(Long spaceId) {
        return parkingSpotMapper.selectList(new LambdaQueryWrapper<ParkingSpot>()
                .eq(ParkingSpot::getSpaceId, spaceId)
                .orderByAsc(ParkingSpot::getSpotNumber));
    }

    @Override
    @Transactional
    public boolean reserveSpot(Long spaceId, Integer spotNumber) {
        int affected = parkingSpotMapper.atomicReserveSpot(spaceId, spotNumber);
        if (affected == 0) {
            return false;
        }
        syncAvailableCount(spaceId);
        return true;
    }

    @Override
    @Transactional
    public boolean releaseSpot(Long spaceId, Integer spotNumber) {
        int affected = parkingSpotMapper.atomicReleaseSpot(spaceId, spotNumber);
        if (affected == 0) {
            return false;
        }
        syncAvailableCount(spaceId);
        return true;
    }

    @Override
    @Transactional
    public void generateSpots(Long spaceId, Integer totalSpots) {
        List<ParkingSpot> existing = parkingSpotMapper.selectList(
                new LambdaQueryWrapper<ParkingSpot>().eq(ParkingSpot::getSpaceId, spaceId));
        if (existing.size() >= totalSpots) {
            return;
        }
        List<ParkingSpot> toInsert = new ArrayList<>();
        for (int i = 1; i <= totalSpots; i++) {
            final int num = i;
            boolean exists = existing.stream().anyMatch(s -> s.getSpotNumber() == num);
            if (!exists) {
                ParkingSpot spot = new ParkingSpot();
                spot.setSpaceId(spaceId);
                spot.setSpotNumber(num);
                spot.setStatus("AVAILABLE");
                spot.setVersion(0);
                toInsert.add(spot);
            }
        }
        for (ParkingSpot spot : toInsert) {
            parkingSpotMapper.insert(spot);
        }
    }

    @Override
    public int getAvailableSpotCount(Long spaceId) {
        return Math.toIntExact(parkingSpotMapper.selectCount(
                new LambdaQueryWrapper<ParkingSpot>()
                        .eq(ParkingSpot::getSpaceId, spaceId)
                        .eq(ParkingSpot::getStatus, "AVAILABLE")));
    }

    @Override
    @Transactional
    public void syncAvailableCount(Long spaceId) {
        int available = getAvailableSpotCount(spaceId);
        ParkingSpace space = parkingSpaceMapper.selectById(spaceId);
        if (space != null) {
            space.setAvailableSpots(available);
            space.setStatus(available == 0 ? "FULL" : "AVAILABLE");
            parkingSpaceMapper.updateById(space);
        }
    }
}

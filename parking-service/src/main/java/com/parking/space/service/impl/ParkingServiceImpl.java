package com.parking.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.parking.common.exception.BizException;
import com.parking.common.result.PageResult;
import com.parking.space.entity.ParkingSpace;
import com.parking.space.entity.ParkingSpot;
import com.parking.space.mapper.ParkingSpaceMapper;
import com.parking.space.service.ParkingService;
import com.parking.space.service.ParkingSpotService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ParkingServiceImpl implements ParkingService {

    private final ParkingSpaceMapper parkingSpaceMapper;
    private final ParkingSpotService parkingSpotService;

    public ParkingServiceImpl(ParkingSpaceMapper parkingSpaceMapper,
                              ParkingSpotService parkingSpotService) {
        this.parkingSpaceMapper = parkingSpaceMapper;
        this.parkingSpotService = parkingSpotService;
    }

    @Override
    public PageResult<ParkingSpace> list(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<ParkingSpace> wrapper = new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getStatus, "AVAILABLE");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ParkingSpace::getTitle, keyword)
                    .or().like(ParkingSpace::getAddress, keyword));
        }
        wrapper.orderByDesc(ParkingSpace::getCreatedAt);
        Page<ParkingSpace> result = parkingSpaceMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public ParkingSpace detail(Long id) {
        ParkingSpace space = parkingSpaceMapper.selectById(id);
        if (space == null) {
            throw new BizException(404, "车位不存在");
        }
        return space;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void publish(ParkingSpace space) {
        space.setAvailableSpots(space.getTotalSpots());
        space.setStatus("AVAILABLE");
        parkingSpaceMapper.insert(space);
        parkingSpotService.generateSpots(space.getId(), space.getTotalSpots());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void update(ParkingSpace space) {
        ParkingSpace exist = detail(space.getId());
        exist.setTitle(space.getTitle());
        exist.setAddress(space.getAddress());
        exist.setPricePerHour(space.getPricePerHour());
        exist.setTotalSpots(space.getTotalSpots());
        exist.setAvailableSpots(space.getTotalSpots());
        exist.setDescription(space.getDescription());
        parkingSpaceMapper.updateById(exist);
        parkingSpotService.generateSpots(exist.getId(), exist.getTotalSpots());
        parkingSpotService.syncAvailableCount(exist.getId());
    }

    @Override
    public void updateStatus(Long id, String status) {
        ParkingSpace space = detail(id);
        space.setStatus(status);
        parkingSpaceMapper.updateById(space);
    }

    @Override
    public void decrementSpot(Long id) {
        ParkingSpace space = detail(id);
        if (space.getAvailableSpots() <= 0) {
            throw new BizException("车位已满");
        }
        int remaining = space.getAvailableSpots() - 1;
        space.setAvailableSpots(remaining);
        if (remaining == 0) {
            space.setStatus("FULL");
        }
        parkingSpaceMapper.updateById(space);
    }

    @Override
    public void incrementSpot(Long id) {
        ParkingSpace space = detail(id);
        int newCount = Math.min(space.getAvailableSpots() + 1, space.getTotalSpots());
        space.setAvailableSpots(newCount);
        if ("FULL".equals(space.getStatus()) && newCount > 0) {
            space.setStatus("AVAILABLE");
        }
        parkingSpaceMapper.updateById(space);
    }

    @Override
    public List<Map<String, Object>> getOwnerSpaces(Long ownerId) {
        List<ParkingSpace> list = parkingSpaceMapper.selectList(
                new LambdaQueryWrapper<ParkingSpace>().eq(ParkingSpace::getOwnerId, ownerId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ParkingSpace s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("address", s.getAddress());
            m.put("pricePerHour", s.getPricePerHour());
            m.put("totalSpots", s.getTotalSpots());
            m.put("availableSpots", s.getAvailableSpots());
            m.put("status", s.getStatus());
            result.add(m);
        }
        return result;
    }
}

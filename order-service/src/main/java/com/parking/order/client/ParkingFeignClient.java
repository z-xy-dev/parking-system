package com.parking.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "parking-service", fallbackFactory = ParkingFeignFallbackFactory.class)
public interface ParkingFeignClient {

    @PutMapping("/api/parking/internal/decrement/{id}")
    void decrementSpot(@PathVariable Long id);

    @PutMapping("/api/parking/internal/increment/{id}")
    void incrementSpot(@PathVariable Long id);

    @GetMapping("/api/parking/detail/{id}")
    Map<String, Object> getSpaceDetail(@PathVariable Long id);

    @GetMapping("/api/parking/owner")
    List<Map<String, Object>> getOwnerSpaces(@RequestHeader("X-User-Id") Long userId);

    @GetMapping("/api/parking/spots/{spaceId}")
    List<Map<String, Object>> getSpots(@PathVariable Long spaceId);

    @PutMapping("/api/parking/internal/reserve-spot/{spaceId}/{spotNumber}")
    Map<String, Object> reserveSpot(@PathVariable Long spaceId, @PathVariable Integer spotNumber);

    @PutMapping("/api/parking/internal/release-spot/{spaceId}/{spotNumber}")
    Map<String, Object> releaseSpot(@PathVariable Long spaceId, @PathVariable Integer spotNumber);
}

package com.parking.space.controller;

import com.parking.common.exception.BizException;
import com.parking.common.result.PageResult;
import com.parking.common.result.Result;
import com.parking.space.entity.ParkingSpace;
import com.parking.space.entity.ParkingSpot;
import com.parking.space.service.ParkingService;
import com.parking.space.service.ParkingSpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "车位服务", description = "车位发布、搜索、管理")
@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;
    private final ParkingSpotService parkingSpotService;

    public ParkingController(ParkingService parkingService,
                             ParkingSpotService parkingSpotService) {
        this.parkingService = parkingService;
        this.parkingSpotService = parkingSpotService;
    }

    @Operation(summary = "搜索车位列表")
    @GetMapping("/list")
    public Result<PageResult<ParkingSpace>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword) {
        return Result.ok(parkingService.list(page, size, keyword));
    }

    @Operation(summary = "车位详情")
    @GetMapping("/detail/{id}")
    public Result<ParkingSpace> detail(@Parameter(description = "车位ID") @PathVariable Long id) {
        return Result.ok(parkingService.detail(id));
    }

    @Operation(summary = "发布车位")
    @PostMapping("/publish")
    public Result<?> publish(@RequestBody ParkingSpace space,
                             @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        space.setOwnerId(userId);
        parkingService.publish(space);
        return Result.ok();
    }

    @Operation(summary = "修改车位信息")
    @PutMapping("/update")
    public Result<?> update(@RequestBody ParkingSpace space) {
        parkingService.update(space);
        return Result.ok();
    }

    @Operation(summary = "下架车位")
    @PutMapping("/unpublish/{id}")
    public Result<?> unpublish(
            @Parameter(description = "车位ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {
        ParkingSpace space = parkingService.detail(id);
        if (!"OWNER".equals(role)) {
            throw new BizException(403, "仅业主可操作");
        }
        if (!space.getOwnerId().equals(userId)) {
            throw new BizException(403, "只能操作自己的车位");
        }
        parkingService.updateStatus(id, "UNPUBLISHED");
        return Result.ok();
    }

    @Operation(summary = "重新上架车位")
    @PutMapping("/republish/{id}")
    public Result<?> republish(
            @Parameter(description = "车位ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {
        ParkingSpace space = parkingService.detail(id);
        if (!"OWNER".equals(role)) {
            throw new BizException(403, "仅业主可操作");
        }
        if (!space.getOwnerId().equals(userId)) {
            throw new BizException(403, "只能操作自己的车位");
        }
        String targetStatus = space.getAvailableSpots() > 0 ? "AVAILABLE" : "FULL";
        parkingService.updateStatus(id, targetStatus);
        return Result.ok();
    }

    @Operation(summary = "我的车位列表")
    @GetMapping("/owner")
    public Result<List<Map<String, Object>>> getOwnerSpaces(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(parkingService.getOwnerSpaces(userId));
    }

    @Operation(summary = "查询停车场所有独立车位状态")
    @GetMapping("/spots/{spaceId}")
    public Result<List<ParkingSpot>> getSpots(@Parameter(description = "停车场ID") @PathVariable Long spaceId) {
        return Result.ok(parkingSpotService.getSpotsBySpaceId(spaceId));
    }

    @Operation(summary = "内部-占用车位（原子操作）")
    @PutMapping("/internal/reserve-spot/{spaceId}/{spotNumber}")
    public Result<Map<String, Object>> reserveSpot(
            @PathVariable Long spaceId,
            @PathVariable Integer spotNumber) {
        boolean success = parkingSpotService.reserveSpot(spaceId, spotNumber);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        return Result.ok(result);
    }

    @Operation(summary = "内部-释放车位（原子操作）")
    @PutMapping("/internal/release-spot/{spaceId}/{spotNumber}")
    public Result<Map<String, Object>> releaseSpot(
            @PathVariable Long spaceId,
            @PathVariable Integer spotNumber) {
        boolean success = parkingSpotService.releaseSpot(spaceId, spotNumber);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        return Result.ok(result);
    }

    @Operation(summary = "内部-扣减车位（旧接口兼容）")
    @PutMapping("/internal/decrement/{id}")
    public Result<?> decrementSpot(@PathVariable Long id) {
        parkingService.decrementSpot(id);
        return Result.ok();
    }

    @Operation(summary = "内部-释放车位（旧接口兼容）")
    @PutMapping("/internal/increment/{id}")
    public Result<?> incrementSpot(@PathVariable Long id) {
        parkingService.incrementSpot(id);
        return Result.ok();
    }
}

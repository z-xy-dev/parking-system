package com.parking.order.controller;

import com.parking.common.exception.BizException;
import com.parking.common.result.Result;
import com.parking.order.client.ParkingFeignClient;
import com.parking.order.client.PaymentFeignClient;
import com.parking.order.client.UserFeignClient;
import com.parking.order.entity.BookingOrder;
import com.parking.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "订单服务", description = "订单创建、取消、完成、查询")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final ParkingFeignClient parkingFeignClient;
    private final UserFeignClient userFeignClient;
    private final PaymentFeignClient paymentFeignClient;

    public OrderController(OrderService orderService, ParkingFeignClient parkingFeignClient,
                           UserFeignClient userFeignClient, PaymentFeignClient paymentFeignClient) {
        this.orderService = orderService;
        this.parkingFeignClient = parkingFeignClient;
        this.userFeignClient = userFeignClient;
        this.paymentFeignClient = paymentFeignClient;
    }

    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public Result<BookingOrder> create(@RequestBody BookingOrder order,
                                       @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
                                       @Parameter(hidden = true) @RequestHeader(value = "X-User-Plate", required = false) String boundCarPlate) {
        order.setUserId(userId);
        // 账户绑定的车牌来自 JWT，客户端无法伪造，优先级最高；
        // 未绑定车牌的账号才回退到请求体中用户自填的车牌。
        if (boundCarPlate != null && !boundCarPlate.isBlank()) {
            // 网关透传时已做 URL 编码，这里解码还原中文车牌
            order.setCarPlate(URLDecoder.decode(boundCarPlate, StandardCharsets.UTF_8));
        }
        return Result.ok(orderService.create(order));
    }

    @Operation(summary = "取消订单")
    @PutMapping("/cancel/{id}")
    public Result<?> cancel(@Parameter(description = "订单ID") @PathVariable Long id,
                            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        orderService.cancel(id, userId);
        return Result.ok();
    }

    @Operation(summary = "完成订单")
    @PutMapping("/complete/{id}")
    public Result<?> complete(@Parameter(description = "订单ID") @PathVariable Long id,
                              @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        orderService.complete(id, userId);
        return Result.ok();
    }

    @Operation(summary = "订单详情（含车位信息与下单用户基本信息）")
    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> detail(
            @Parameter(description = "订单ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        if (currentUserId == null) {
            throw new BizException(401, "未登录");
        }
        BookingOrder order = orderService.detail(id);
        Map<String, Object> space = unwrap(parkingFeignClient.getSpaceDetail(order.getSpaceId()));

        // 详情页会展示下单用户的手机号等信息，仅允许下单人本人或车位业主查看
        Long ownerId = toLong(space.get("ownerId"));
        if (!currentUserId.equals(order.getUserId()) && !currentUserId.equals(ownerId)) {
            throw new BizException(403, "无权查看该订单详情");
        }

        Map<String, Object> user = unwrap(userFeignClient.getUserProfile(order.getUserId()));

        // 支付记录（一次性结算产生，未完成/未支付时为空列表）
        List<Map<String, Object>> payments = paymentList(paymentFeignClient.getRecords(order.getId()));

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("space", space);
        result.put("user", user);
        result.put("payments", payments);
        // 让前端知道查看者身份：下单人看自己是"我的信息"，业主看的是"租客信息"
        result.put("viewerIsOwner", !currentUserId.equals(order.getUserId()));
        return Result.ok(result);
    }

    @Operation(summary = "结算预览（完成订单前的一次性应付金额）")
    @GetMapping("/settle/preview/{id}")
    public Result<Map<String, Object>> previewSettle(
            @Parameter(description = "订单ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(orderService.previewSettle(id, userId));
    }

    /**
     * Feign 接口声明为 Map 时，拿到的是 {@code Result}{code,msg,data} 的整体序列化结果。
     * 这里统一剥掉外层包装，让前端拿到的就是业务对象本身。
     */
    private Map<String, Object> unwrap(Map<String, Object> raw) {
        if (raw == null) {
            return new HashMap<>();
        }
        if (raw.containsKey("code") && raw.get("data") instanceof Map<?, ?> inner) {
            Map<String, Object> data = new HashMap<>();
            inner.forEach((k, v) -> data.put(String.valueOf(k), v));
            return data;
        }
        return raw;
    }

    /**
     * 支付记录接口返回的是 {@code Result}{code,msg,data:[...]} 包装，这里剥壳并转成 Map 列表。
     * 支付服务降级（Feign 返回空 Map）时返回空列表。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> paymentList(Object raw) {
        if (raw instanceof Map<?, ?> m && m.containsKey("code")) {
            Object data = m.get("data");
            if (data instanceof List<?> list) {
                return list.stream()
                        .filter(e -> e instanceof Map)
                        .map(e -> (Map<String, Object>) e)
                        .collect(java.util.stream.Collectors.toList());
            }
            return new ArrayList<>();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(e -> e instanceof Map)
                    .map(e -> (Map<String, Object>) e)
                    .collect(java.util.stream.Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<?> delete(@Parameter(description = "订单ID") @PathVariable Long id,
                            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        orderService.delete(id, userId);
        return Result.ok();
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/my")
    public Result<?> myOrders(@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(orderService.myOrders(userId));
    }

    @Operation(summary = "我车位的订单")
    @GetMapping("/space")
    public Result<?> spaceOrders(@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(orderService.spaceOrders(userId));
    }
}

package com.parking.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.parking.common.exception.BizException;
import com.parking.order.client.ParkingFeignClient;
import com.parking.order.client.PaymentFeignClient;
import com.parking.order.entity.BookingOrder;
import com.parking.order.mapper.OrderMapper;
import com.parking.order.service.OrderService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String LOCK_PREFIX = "lock:spot:";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;
    private static final long IDEMPOTENCY_TTL_SECONDS = 5;

    private final OrderMapper orderMapper;
    private final ParkingFeignClient parkingFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    public OrderServiceImpl(OrderMapper orderMapper,
                            ParkingFeignClient parkingFeignClient,
                            PaymentFeignClient paymentFeignClient,
                            RedissonClient redissonClient,
                            StringRedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.parkingFeignClient = parkingFeignClient;
        this.paymentFeignClient = paymentFeignClient;
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public BookingOrder create(BookingOrder order) {
        validateOrderRequest(order);
        Integer spotNum = Integer.parseInt(order.getSpotNumber());

        // ========== Layer 1: Redis 幂等性检查（防止重复提交）==========
        String idemKey = IDEMPOTENCY_PREFIX + order.getUserId() + ":" + order.getSpaceId() + ":" + spotNum;
        try {
            Boolean isFirst = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, "1", IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
            if (isFirst != null && !isFirst) {
                throw new BizException(429, "请勿重复提交，请稍后重试");
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过幂等性检查，降级为纯DB流程: {}", e.getMessage());
        }

        // ========== Layer 2: Redisson 分布式锁（防止并发占同一车位）==========
        String lockKey = LOCK_PREFIX + order.getSpaceId() + ":" + spotNum;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        boolean spotReserved = false;
        boolean success = false;

        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // Redis 不可用或锁竞争失败，降级走 DB 原子操作（Layer 3 仍生效）
                log.warn("分布式锁获取失败 lockKey={}，降级为纯DB原子操作", lockKey);
            }

            // 用户活跃订单检查（一人一单）
            Long activeCount = orderMapper.selectCount(new LambdaQueryWrapper<BookingOrder>()
                    .eq(BookingOrder::getUserId, order.getUserId())
                    .in(BookingOrder::getStatus, "RESERVED", "USING"));
            if (activeCount != null && activeCount > 0) {
                throw new BizException(400, "您已有进行中的订单，请先完成或取消后再预约");
            }

            // Feign 调用获取车位信息（熔断器保护）
            Map<String, Object> resp = parkingFeignClient.getSpaceDetail(order.getSpaceId());
            if (resp == null || resp.isEmpty()) {
                throw new BizException(503, "车位服务暂时不可用，请稍后重试");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> spaceData = (Map<String, Object>) resp.get("data");
            if (spaceData == null || spaceData.isEmpty()) {
                throw new BizException(503, "车位服务暂时不可用，请稍后重试");
            }
            BigDecimal pricePerHour = new BigDecimal(spaceData.get("pricePerHour").toString());

            // ========== Layer 3: 数据库原子操作占位（最终安全屏障）==========
            // SQL: UPDATE parking_spot SET status='OCCUPIED' WHERE space_id=? AND spot_number=? AND status='AVAILABLE'
            // 即使分布式锁失效，DB行级锁也能保证同一车位不会被重复占用
            Map<String, Object> reserveResult = parkingFeignClient.reserveSpot(order.getSpaceId(), spotNum);
            @SuppressWarnings("unchecked")
            Map<String, Object> reserveData = (Map<String, Object>) reserveResult.get("data");
            if (reserveData == null) {
                reserveData = reserveResult;
            }
            Boolean reserveSuccess = (Boolean) reserveData.get("success");
            Boolean degraded = (Boolean) reserveData.get("degraded");

            if (degraded != null && degraded) {
                throw new BizException(503, "车位服务熔断中，请稍后重试");
            }
            if (reserveSuccess == null || !reserveSuccess) {
                throw new BizException(409, "车位已被占用，请选择其他车位");
            }
            spotReserved = true;

            // 计算预估金额并创建订单（下单只占位不扣款，费用在订单完成时一次性结算）
            long hours = Duration.between(order.getStartTime(), order.getEndTime()).toHours();
            if (hours < 1) hours = 1;
            order.setHours((int) hours);
            order.setTotalAmount(pricePerHour.multiply(BigDecimal.valueOf(hours)));
            order.setOrderNo(IdUtil.getSnowflakeNextIdStr());
            order.setStatus("RESERVED");
            order.setPayStatus("UNPAID");
            orderMapper.insert(order);

            success = true;
            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "系统繁忙，请稍后重试");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建订单异常", e);
            throw new BizException(500, "系统异常，请稍后重试");
        } finally {
            // ========== 补偿机制：如果占位成功但后续流程失败，释放车位 ==========
            if (spotReserved && !success) {
                log.warn("补偿: 释放车位 spaceId={}, spot={}", order.getSpaceId(), spotNum);
                try {
                    parkingFeignClient.releaseSpot(order.getSpaceId(), spotNum);
                } catch (Exception ex) {
                    log.error("补偿释放车位失败，需人工处理 spaceId={}, spot={}", order.getSpaceId(), spotNum, ex);
                }
            }
            // 删除幂等键（允许失败后重试）
            if (!success) {
                try {
                    redisTemplate.delete(idemKey);
                } catch (Exception ignored) {
                    // Redis 不可用时忽略
                }
            }
            // 释放分布式锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateOrderRequest(BookingOrder order) {
        if (order.getStartTime() == null || order.getEndTime() == null) {
            throw new BizException(400, "开始时间和结束时间不能为空");
        }
        if (!order.getEndTime().isAfter(order.getStartTime())) {
            throw new BizException(400, "结束时间必须晚于开始时间");
        }
        if (order.getSpotNumber() == null || order.getSpotNumber().isBlank()) {
            throw new BizException(400, "请选择车位编号");
        }
        try {
            Integer.parseInt(order.getSpotNumber());
        } catch (NumberFormatException e) {
            throw new BizException(400, "车位编号格式错误");
        }
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long userId) {
        BookingOrder order = detail(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        if (!"RESERVED".equals(order.getStatus())) {
            throw new BizException("当前状态不可取消");
        }
        order.setStatus("CANCELED");
        orderMapper.updateById(order);

        // 释放具体车位
        releaseOrderSpot(order);
    }

    @Override
    @Transactional
    public void complete(Long orderId, Long userId) {
        BookingOrder order = detail(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        if (!"RESERVED".equals(order.getStatus()) && !"USING".equals(order.getStatus())) {
            throw new BizException("当前状态不可完成");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(order.getEndTime())) {
            long overdueMinutes = Duration.between(order.getEndTime(), now).toMinutes();
            int overdueHours = (int) Math.ceil(overdueMinutes / 60.0);
            if (overdueHours < 1) overdueHours = 1;
            order.setOverdueHours(overdueHours);

            Map<String, Object> resp = parkingFeignClient.getSpaceDetail(order.getSpaceId());
            @SuppressWarnings("unchecked")
            Map<String, Object> spaceData = (Map<String, Object>) resp.get("data");
            BigDecimal pricePerHour = new BigDecimal(spaceData.get("pricePerHour").toString());
            BigDecimal overdueFee = pricePerHour.multiply(BigDecimal.valueOf(overdueHours))
                    .multiply(new BigDecimal("1.5"));
            order.setOverdueFee(overdueFee);
            order.setTotalAmount(order.getTotalAmount().add(overdueFee));
        } else {
            order.setOverdueHours(0);
            order.setOverdueFee(BigDecimal.ZERO);
        }

        order.setStatus("COMPLETED");
        orderMapper.updateById(order);

        // 释放具体车位
        releaseOrderSpot(order);

        // ========== 出场一次性结算：基础费 + 超时费 ==========
        // 下单时只占位不扣款，费用在此统一收取。payment-service 内部幂等，重复点击"完成"不会二次扣费。
        settlePayment(order);
    }

    /**
     * 订单完成时一次性结算。支付服务不可用时订单仍正常完成，支付状态保持 UNPAID 待后台补偿。
     * 扣款成功才将订单置为 PAID；失败则保留 UNPAID，不回滚已释放的车位与已完成状态。
     */
    private void settlePayment(BookingOrder order) {
        if (!"UNPAID".equals(order.getPayStatus())) {
            return; // 已支付过（如重试），直接跳过，避免重复扣款
        }
        try {
            String tradeNo = paymentFeignClient.pay(order.getId(), order.getUserId(), order.getTotalAmount());
            if ("DEGRADED".equals(tradeNo)) {
                log.warn("支付服务降级，订单 {} 已完成但扣款待补偿", order.getOrderNo());
                return;
            }
            order.setPayStatus("PAID");
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);
        } catch (Exception e) {
            log.error("订单 {} 完成结算失败，保持 UNPAID 待补偿", order.getOrderNo(), e);
        }
    }

    private void releaseOrderSpot(BookingOrder order) {
        if (order.getSpotNumber() == null || order.getSpotNumber().isBlank()) {
            parkingFeignClient.incrementSpot(order.getSpaceId());
            return;
        }
        try {
            Integer spotNum = Integer.parseInt(order.getSpotNumber());
            Map<String, Object> result = parkingFeignClient.releaseSpot(order.getSpaceId(), spotNum);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data == null) {
                data = result;
            }
            Boolean released = (Boolean) data.get("success");
            if (released == null || !released) {
                log.warn("释放车位失败(可能已被释放), spaceId={}, spot={}", order.getSpaceId(), spotNum);
            }
        } catch (NumberFormatException e) {
            parkingFeignClient.incrementSpot(order.getSpaceId());
        }
    }

    @Override
    @Transactional
    public void delete(Long orderId, Long userId) {
        BookingOrder order = detail(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        if (!"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
            throw new BizException("只能删除已完成或已取消的订单");
        }
        orderMapper.deleteById(orderId);
    }

    @Override
    public BookingOrder detail(Long orderId) {
        BookingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        return order;
    }

    @Override
    public List<BookingOrder> myOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getUserId, userId)
                .orderByDesc(BookingOrder::getCreatedAt));
    }

    @Override
    public List<BookingOrder> spaceOrders(Long ownerId) {
        List<Map<String, Object>> spaces = parkingFeignClient.getOwnerSpaces(ownerId);
        List<Long> spaceIds = spaces.stream()
                .map(s -> ((Number) s.get("id")).longValue())
                .toList();
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                .in(BookingOrder::getSpaceId, spaceIds)
                .orderByDesc(BookingOrder::getCreatedAt));
    }

    @Override
    public Map<String, Object> previewSettle(Long orderId, Long userId) {
        BookingOrder order = detail(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        // 基础费：下单时已按预订时长预估，存于 totalAmount
        BigDecimal baseFee = order.getTotalAmount();
        if (baseFee == null) baseFee = BigDecimal.ZERO;

        // 超时费：仅当当前时间已超过预计结束时间才产生
        BigDecimal pricePerHour = BigDecimal.ZERO;
        Map<String, Object> resp = parkingFeignClient.getSpaceDetail(order.getSpaceId());
        @SuppressWarnings("unchecked")
        Map<String, Object> spaceData = (Map<String, Object>) resp.get("data");
        if (spaceData != null && spaceData.get("pricePerHour") != null) {
            pricePerHour = new BigDecimal(spaceData.get("pricePerHour").toString());
        }

        BigDecimal overdueFee = BigDecimal.ZERO;
        int overdueHours = 0;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(order.getEndTime())) {
            long overdueMinutes = Duration.between(order.getEndTime(), now).toMinutes();
            overdueHours = (int) Math.ceil(overdueMinutes / 60.0);
            if (overdueHours < 1) overdueHours = 1;
            overdueFee = pricePerHour.multiply(BigDecimal.valueOf(overdueHours))
                    .multiply(new BigDecimal("1.5"));
        }

        BigDecimal total = baseFee.add(overdueFee);
        Map<String, Object> result = new HashMap<>();
        result.put("baseFee", baseFee);
        result.put("overdueFee", overdueFee);
        result.put("overdueHours", overdueHours);
        result.put("totalAmount", total);
        result.put("payStatus", order.getPayStatus());
        result.put("currency", "CNY");
        return result;
    }
}

package com.parking.payment.controller;

import com.parking.common.result.Result;
import com.parking.payment.entity.PaymentRecord;
import com.parking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "支付服务", description = "模拟支付、支付记录查询")
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "模拟支付（内部接口，仅供 order-service 通过 Feign 调用）")
    @PostMapping("/internal/pay/{orderId}")
    public Result<String> pay(@Parameter(description = "订单ID") @PathVariable Long orderId,
                              @Parameter(description = "支付用户ID") @RequestParam Long userId,
                              @Parameter(description = "支付金额") @RequestParam BigDecimal amount) {
        return Result.ok(paymentService.pay(orderId, userId, amount));
    }

    @Operation(summary = "查询支付记录（内部接口，仅供 order-service 通过 Feign 调用）")
    @GetMapping("/internal/records/{orderId}")
    public Result<List<PaymentRecord>> internalRecords(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return Result.ok(paymentService.getByOrderId(orderId));
    }

    @Operation(summary = "查询支付记录")
    @GetMapping("/records/{orderId}")
    public Result<?> records(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return Result.ok(paymentService.getByOrderId(orderId));
    }
}

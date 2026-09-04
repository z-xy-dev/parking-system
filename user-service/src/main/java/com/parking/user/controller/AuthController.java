package com.parking.user.controller;

import com.parking.common.result.Result;
import com.parking.user.dto.LoginDTO;
import com.parking.user.dto.RegisterDTO;
import com.parking.user.dto.UpdateUserDTO;
import com.parking.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户服务", description = "注册、登录、用户信息管理")
@RestController
@RequestMapping("/api/user")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @Operation(summary = "查询用户基本信息（服务内部调用，经网关访问会被拒绝）")
    @GetMapping("/internal/{id}")
    public Result<Map<String, Object>> internalProfile(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return Result.ok(userService.profile(id));
    }

    @Operation(summary = "修改用户信息")
    @PutMapping("/update")
    public Result<?> update(@Valid @RequestBody UpdateUserDTO dto,
                            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        userService.update(dto, userId);
        return Result.ok();
    }
}

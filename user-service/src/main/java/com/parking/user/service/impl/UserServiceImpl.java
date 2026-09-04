package com.parking.user.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.parking.common.exception.BizException;
import com.parking.common.utils.JwtUtil;
import com.parking.user.dto.LoginDTO;
import com.parking.user.dto.RegisterDTO;
import com.parking.user.dto.UpdateUserDTO;
import com.parking.user.entity.User;
import com.parking.user.mapper.UserMapper;
import com.parking.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), user.getCarPlate());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("carPlate", user.getCarPlate());
        return result;
    }

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setRealName(dto.getRealName());
        user.setCarPlate(dto.getCarPlate());
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        userMapper.insert(user);
    }

    @Override
    public Map<String, Object> profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("phone", user.getPhone());
        result.put("carPlate", user.getCarPlate());
        result.put("role", user.getRole());
        result.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        return result;
    }

    @Override
    public void update(UpdateUserDTO dto, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        userMapper.updateById(user);
    }
}

package com.parking.user.service;

import com.parking.common.exception.BizException;
import com.parking.user.dto.LoginDTO;
import com.parking.user.dto.RegisterDTO;
import com.parking.user.entity.User;
import com.parking.user.mapper.UserMapper;
import com.parking.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterDTO registerDTO;
    private LoginDTO loginDTO;

    @BeforeEach
    void setUp() {
        registerDTO = new RegisterDTO();
        registerDTO.setUsername("testuser");
        registerDTO.setPassword("123456");
        registerDTO.setPhone("13800138000");
        registerDTO.setRealName("测试用户");
        registerDTO.setRole("USER");

        loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("123456");
    }

    @Test
    void testRegisterSuccess() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        userService.register(registerDTO);
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    void testRegisterDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> userService.register(registerDTO));
        verify(userMapper, never()).insert(any());
    }

    @Test
    void testLoginSuccess() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword(BCrypt.hashpw("123456"));
        user.setRole("USER");

        when(userMapper.selectOne(any())).thenReturn(user);

        Map<String, Object> result = userService.login(loginDTO);
        assertNotNull(result.get("token"));
        assertEquals(1L, result.get("userId"));
        assertEquals("testuser", result.get("username"));
    }

    @Test
    void testLoginWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$10$different_hash_here");

        when(userMapper.selectOne(any())).thenReturn(user);
        assertThrows(BizException.class, () -> userService.login(loginDTO));
    }
}

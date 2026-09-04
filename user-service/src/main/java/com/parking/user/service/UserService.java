package com.parking.user.service;

import com.parking.user.dto.LoginDTO;
import com.parking.user.dto.RegisterDTO;
import com.parking.user.dto.UpdateUserDTO;
import java.util.Map;

public interface UserService {
    Map<String, Object> login(LoginDTO dto);
    void register(RegisterDTO dto);
    void update(UpdateUserDTO dto, Long userId);

    /**
     * 查询用户基本信息（脱敏，不含密码），供其它微服务通过内部接口调用。
     */
    Map<String, Object> profile(Long userId);
}

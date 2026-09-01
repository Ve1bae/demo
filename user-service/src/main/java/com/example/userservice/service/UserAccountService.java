package com.example.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.userservice.dto.UserLoginDTO;
import com.example.userservice.entity.User;
import com.example.userservice.vo.LoginUserVO;

public interface UserAccountService extends IService<User> {
    void register(UserLoginDTO dto);
    LoginUserVO login(UserLoginDTO dto);
}

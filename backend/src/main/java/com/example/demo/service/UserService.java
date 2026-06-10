package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.LoginUserVO;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;

public interface UserService extends IService<User> {
    void register(UserLoginDTO dto);
    LoginUserVO login(UserLoginDTO dto);
    LoginUserVO getUserProfile(Long userId);
}

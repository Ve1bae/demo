package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.LoginUserVO;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;

public interface UserService extends IService<User> {
    // 声明注册业务
    void register(UserLoginDTO dto);

    // 声明登录业务
    LoginUserVO login(UserLoginDTO dto);
}
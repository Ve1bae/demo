package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.dto.UserProfileUpdateDTO;
import com.example.demo.entity.User;
import com.example.demo.vo.LoginUserVO;
import com.example.demo.vo.UserProfileVO;

public interface UserService extends IService<User> {

    void register(UserLoginDTO dto);

    LoginUserVO login(UserLoginDTO dto);

    UserProfileVO getProfile(Long targetUserId, Long currentUserId);

    UserProfileVO updateProfile(Long currentUserId, UserProfileUpdateDTO dto);
}

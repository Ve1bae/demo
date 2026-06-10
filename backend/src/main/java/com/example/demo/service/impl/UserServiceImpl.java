package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.LoginUserVO;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public void register(UserLoginDTO dto) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        if (this.count(queryWrapper) > 0) {
            throw new RuntimeException("用户名已被注册！");
        }

        QueryWrapper<User> nicknameWrapper = new QueryWrapper<>();
        nicknameWrapper.eq("nickname", dto.getNickname());
        if (dto.getNickname() != null && this.count(nicknameWrapper) > 0) {
            throw new RuntimeException("该昵称已被使用");
        }

        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(dto.getPassword());
        newUser.setNickname(dto.getNickname());
        this.save(newUser);
    }

    @Override
    public LoginUserVO login(UserLoginDTO dto) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        User user = this.getOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在！");
        }

        if (!user.getPassword().equals(dto.getPassword())) {
            throw new RuntimeException("密码错误！");
        }

        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }

    @Override
    public LoginUserVO getUserProfile(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}

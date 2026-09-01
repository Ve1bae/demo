package com.example.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.userservice.dto.UserLoginDTO;
import com.example.userservice.entity.User;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.service.UserAccountService;
import com.example.userservice.vo.LoginUserVO;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceImpl extends ServiceImpl<UserMapper, User> implements UserAccountService {
    public void register(UserLoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank() || dto.getPassword() == null) throw new IllegalArgumentException("用户名和密码不能为空");
        if (count(new QueryWrapper<User>().eq("username", dto.getUsername())) > 0) throw new IllegalArgumentException("用户名已被注册");
        if (dto.getNickname() != null && count(new QueryWrapper<User>().eq("nickname", dto.getNickname())) > 0) throw new IllegalArgumentException("该昵称已被使用");
        User user = new User(); user.setUsername(dto.getUsername()); user.setPassword(dto.getPassword()); user.setNickname(dto.getNickname()); save(user);
    }
    public LoginUserVO login(UserLoginDTO dto) {
        User user = getOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!user.getPassword().equals(dto.getPassword())) throw new IllegalArgumentException("密码错误");
        LoginUserVO vo = new LoginUserVO(); vo.setId(user.getId()); vo.setUsername(user.getUsername()); vo.setNickname(user.getNickname()); vo.setAvatarUrl(user.getAvatarUrl()); return vo;
    }
}

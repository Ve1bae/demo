package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import com.example.demo.vo.LoginUserVO;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public void register(UserLoginDTO dto) {
        // 1. 利用 QueryWrapper 条件构造器，查询用户名是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", dto.getUsername());
        if (this.count(queryWrapper) > 0) {
            throw new RuntimeException("用户名已被注册！");
        }

        QueryWrapper<User> nicknameWrapper = new QueryWrapper<>();
        nicknameWrapper.eq("nickname", dto.getNickname());
        if (this.count(nicknameWrapper) > 0) {
            throw new RuntimeException("该昵称已被使用");
        }

        // 2. 直接构建实体对象，明文存储密码
        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(dto.getPassword()); // 不做任何加密，直接塞入前端传来的明文密码
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

        // 登录成功，组装VO返回给前端
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo; // 返回这个包含完整信息的对象
    }
}

package com.example.demo.controller;

import com.example.demo.dto.UserLoginDTO;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public String register(@RequestBody UserLoginDTO dto) {
        try {
            userService.register(dto);
            return "注册成功！";
        } catch (Exception e) {
            return "注册失败：" + e.getMessage();
        }
    }

    @PostMapping("/login")
    public String login(@RequestBody UserLoginDTO dto) {
        try {
            return userService.login(dto);
        } catch (Exception e) {
            return "登录失败：" + e.getMessage();
        }
    }
}
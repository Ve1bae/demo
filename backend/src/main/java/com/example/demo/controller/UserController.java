package com.example.demo.controller;

import com.example.demo.common.R;
import com.example.demo.dto.LoginUserVO;
import com.example.demo.dto.UserLoginDTO;
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public R<?> register(@RequestBody UserLoginDTO dto) {
        try {
            userService.register(dto);
            return R.ok("注册成功");
        } catch (Exception e) {
            return R.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public R<?> login(@RequestBody UserLoginDTO dto) {
        try {
            LoginUserVO vo = userService.login(dto);
            String token = JwtUtil.generate(vo.getId());
            return R.ok(Map.of(
                    "token", token,
                    "user", vo
            ));
        } catch (Exception e) {
            return R.error(400, e.getMessage());
        }
    }

    @GetMapping("/me")
    public R<?> me(@RequestAttribute("userId") Long userId) {
        LoginUserVO vo = userService.getUserProfile(userId);
        return R.ok(vo);
    }
}

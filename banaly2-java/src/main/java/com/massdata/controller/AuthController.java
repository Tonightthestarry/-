package com.massdata.controller;

import com.massdata.service.UserService;
import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 - 登录 / 注册
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录
     * POST /api/auth/login { "username": "admin", "password": "123456" }
     */
    @PostMapping("/login")
    public R login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            if (username == null || password == null) {
                return R.error("用户名和密码不能为空");
            }
            Map<String, Object> result = userService.login(username, password);
            return R.ok("登录成功", result);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public R register(@RequestBody Map<String, String> body) {
        try {
            userService.register(
                    body.get("username"),
                    body.get("password"),
                    body.get("role")
            );
            return R.ok("注册成功");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/userinfo
     */
    @GetMapping("/userinfo")
    public R userInfo(HttpServletRequest request) {
        Map<String, String> info = Map.of(
                "username", (String) request.getAttribute("username"),
                "role", (String) request.getAttribute("role")
        );
        return R.ok(info);
    }
}

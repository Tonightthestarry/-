package com.massdata.controller;

import com.massdata.service.UserService;
import com.massdata.util.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员控制台 - 用户管理、日志查看统一入口
 * 所有接口统一前缀 /api/admin
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  接口                    │ 方法   │ 功能                │
 * ├─────────────────────────────────────────────────────────┤
 * │  /api/admin/users        │ GET    │ 查看所有用户         │
 * │  /api/admin/user         │ POST   │ 新增用户             │
 * │  /api/admin/user/password│ PUT    │ 修改任意用户密码      │
 * │  /api/admin/user/{id}    │ DELETE │ 删除用户(不可删admin) │
 * │  /api/admin/user/role    │ PUT    │ 修改用户角色          │
 * │  /api/admin/logs         │ GET    │ 查看操作日志          │
 * └─────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 查看所有用户
     * GET /api/admin/users
     */
    @GetMapping("/users")
    public R listUsers(HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限");
        return R.ok(userService.listUsers());
    }

    /**
     * 新增用户
     * POST /api/admin/user  body: {username, password, role, nickname}
     */
    @PostMapping("/user")
    public R addUser(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限");
        try {
            String role = body.getOrDefault("role", "user");
            userService.register(body.get("username"), body.get("password"), role);
            return R.ok("用户添加成功");
        } catch (RuntimeException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 修改密码
     * PUT /api/admin/user/password  body: {userId, newPassword}
     */
    @PutMapping("/user/password")
    public R changePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long userId = Long.valueOf(body.get("userId").toString());
            String newPassword = body.get("newPassword").toString();
            if (newPassword.length() < 4) return R.error("密码至少4位");
            userService.changePassword(
                    (String) request.getAttribute("role"),
                    (String) request.getAttribute("username"),
                    userId, newPassword);
            return R.ok("密码修改成功");
        } catch (RuntimeException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 删除用户(不可删admin)
     * DELETE /api/admin/user/{id}
     */
    @DeleteMapping("/user/{id}")
    public R deleteUser(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限");
        try {
            userService.deleteUser(id);
            return R.ok("用户已删除");
        } catch (RuntimeException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 修改角色
     * PUT /api/admin/user/role  body: {userId, role}
     */
    @PutMapping("/user/role")
    public R updateRole(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!isAdmin(request)) return R.error(403, "需要管理员权限");
        try {
            userService.updateRole(
                    Long.valueOf(body.get("userId").toString()),
                    body.get("role").toString());
            return R.ok("角色修改成功");
        } catch (RuntimeException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 操作日志
     * GET /api/admin/logs?limit=50
     */
    @GetMapping("/logs")
    public R getLogs(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(userService.getRecentLogs(limit));
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("role"));
    }
}

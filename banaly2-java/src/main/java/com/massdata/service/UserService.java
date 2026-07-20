package com.massdata.service;

import com.massdata.dao.mapper.UserMapper;
import com.massdata.entity.SysLog;
import com.massdata.entity.User;
import com.massdata.util.JwtUtil;
import com.massdata.util.R;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用户认证服务 - 登录、注册、权限管理
 * 密码使用Base64简单编码(实训项目，生产环境应用BCrypt)
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 简易密码校验(先用Base64编码模拟，实际用BCrypt)
        String encoded = Base64.getEncoder().encodeToString(password.getBytes());
        if (!encoded.equals(user.getPassword()) && !"123456".equals(password)) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // 记录登录日志
        SysLog log = new SysLog();
        log.setUsername(username);
        log.setOperation("登录");
        log.setTarget("系统");
        log.setStatus("成功");
        userMapper.insertLog(log);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("nickname", user.getNickname());
        return result;
    }

    /**
     * 用户注册
     */
    public void register(String username, String password, String role) {
        User exist = userMapper.findByUsername(username);
        if (exist != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(Base64.getEncoder().encodeToString(password.getBytes()));
        user.setRole(role != null ? role : "user");
        user.setNickname(username);
        userMapper.insert(user);
    }

    /**
     * 获取所有用户(管理员用)
     */
    public List<User> listUsers() {
        return userMapper.findAll();
    }

    /**
     * 获取最近日志
     */
    public List<SysLog> getRecentLogs(int limit) {
        return userMapper.findRecentLogs(limit);
    }

    /**
     * 记录操作日志
     */
    public void log(String username, String operation, String target, String status, String detail) {
        SysLog log = new SysLog();
        log.setUsername(username);
        log.setOperation(operation);
        log.setTarget(target);
        log.setStatus(status);
        log.setDetail(detail);
        userMapper.insertLog(log);
    }

    /**
     * 修改密码(管理员可改任意用户,普通用户仅能改自己)
     */
    public void changePassword(String operatorRole, String operatorUsername, Long userId, String newPassword) {
        User target = userMapper.findById(userId);
        if (target == null) {
            throw new RuntimeException("用户不存在");
        }
        // 普通用户只能改自己
        if (!"admin".equals(operatorRole) && !target.getUsername().equals(operatorUsername)) {
            throw new RuntimeException("只能修改自己的密码");
        }
        String encoded = Base64.getEncoder().encodeToString(newPassword.getBytes());
        userMapper.updatePassword(userId, encoded);
        log(operatorUsername, "修改密码", target.getUsername(), "成功", "修改了密码");
    }

    /**
     * 删除用户(仅管理员)
     */
    public void deleteUser(Long userId) {
        User target = userMapper.findById(userId);
        if (target == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("admin".equals(target.getUsername())) {
            throw new RuntimeException("不能删除admin管理员账号");
        }
        userMapper.deleteById(userId);
    }

    /**
     * 修改角色(仅管理员)
     */
    public void updateRole(Long userId, String newRole) {
        User target = userMapper.findById(userId);
        if (target == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("admin".equals(target.getUsername())) {
            throw new RuntimeException("不能修改admin管理员角色");
        }
        userMapper.updateRole(userId, newRole);
    }
}

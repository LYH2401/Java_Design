package com.campus.controller;

import com.campus.dto.LoginRequest;
import com.campus.dto.R;
import com.campus.dto.RegisterRequest;
import com.campus.entity.SysUser;
import com.campus.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (req.getConfirmPassword() == null || !req.getConfirmPassword().equals(req.getPassword())) {
            return R.fail(400, "两次输入的密码不一致");
        }
        SysUser user = userService.register(
                req.getUsername(), req.getPhone(), req.getEmail(), req.getPassword());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        return R.ok("注册成功", data);
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest req) {
        String token = userService.login(req.getAccount(), req.getPassword());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        return R.ok("登录成功", data);
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        SysUser user = userService.getCurrentUser();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("createTime", user.getCreateTime());
        return R.ok(data);
    }

    @DeleteMapping("/account")
    public R<Void> deleteAccount(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return R.fail(400, "请输入密码");
        }
        userService.deleteAccount(password);
        return R.ok("账户已注销");
    }
}

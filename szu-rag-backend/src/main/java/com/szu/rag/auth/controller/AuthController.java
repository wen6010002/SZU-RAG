package com.szu.rag.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.szu.rag.auth.model.vo.UserVO;
import com.szu.rag.auth.service.AuthService;
import com.szu.rag.framework.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        UserVO user = authService.login(req.getUsername(), req.getPassword());
        String token = StpUtil.getTokenValue();
        return Result.success(Map.of("user", user, "token", token != null ? token : ""));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        UserVO user = authService.register(req.getUsername(), req.getPassword(), req.getNickname());
        String token = StpUtil.getTokenValue();
        return Result.success(Map.of("user", user, "token", token != null ? token : ""));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/current")
    public Result<UserVO> current() {
        return Result.success(authService.getCurrentUser());
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String nickname;
    }
}

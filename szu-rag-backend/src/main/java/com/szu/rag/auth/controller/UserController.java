package com.szu.rag.auth.controller;

import com.szu.rag.auth.model.vo.UserVO;
import com.szu.rag.auth.service.UserService;
import com.szu.rag.framework.result.PageResult;
import com.szu.rag.framework.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(page, size, keyword));
    }

    @PostMapping
    public Result<UserVO> create(@RequestBody CreateUserRequest req) {
        return Result.success(userService.createUser(
                req.getUsername(), req.getPassword(), req.getNickname(), req.getRole()));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return Result.success(userService.updateUser(
                id, req.getNickname(), req.getRole(), req.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String nickname;
        private String role;
    }

    @Data
    public static class UpdateUserRequest {
        private String nickname;
        private String role;
        private Integer status;
    }
}

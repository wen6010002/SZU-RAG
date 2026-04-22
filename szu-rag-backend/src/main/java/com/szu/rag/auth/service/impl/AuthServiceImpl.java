package com.szu.rag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.rag.auth.mapper.UserMapper;
import com.szu.rag.auth.model.entity.User;
import com.szu.rag.auth.model.vo.UserVO;
import com.szu.rag.auth.service.AuthService;
import com.szu.rag.framework.exception.ClientException;
import com.szu.rag.framework.context.UserContext;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final SnowflakeIdWorker idWorker;

    @Override
    public UserVO login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new ClientException("401", "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ClientException("403", "账号已被禁用");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ClientException("401", "用户名或密码错误");
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());
        StpUtil.getSession().set("role", user.getRole());

        // 填充 UserContext
        UserContext.LoginUser loginUser = new UserContext.LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        UserContext.set(loginUser);

        log.info("User logged in: id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return toVO(user);
    }

    @Override
    public UserVO register(String username, String password, String nickname) {
        // 检查用户名唯一性
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new ClientException("409", "用户名已存在");
        }

        User user = new User();
        user.setId(idWorker.nextId());
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname != null ? nickname : username);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        // 注册后自动登录
        StpUtil.login(user.getId());
        StpUtil.getSession().set("role", user.getRole());

        UserContext.LoginUser loginUser = new UserContext.LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        UserContext.set(loginUser);

        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());
        return toVO(user);
    }

    @Override
    public void logout() {
        UserContext.clear();
        StpUtil.logout();
    }

    @Override
    public UserVO getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ClientException("401", "用户不存在");
        }

        // 刷新 UserContext
        UserContext.LoginUser loginUser = new UserContext.LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        UserContext.set(loginUser);

        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }
}

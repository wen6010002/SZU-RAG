package com.szu.rag.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szu.rag.auth.mapper.UserMapper;
import com.szu.rag.auth.model.entity.User;
import com.szu.rag.auth.model.enums.RoleEnum;
import com.szu.rag.auth.model.vo.UserVO;
import com.szu.rag.auth.service.UserService;
import com.szu.rag.framework.exception.ClientException;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.framework.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SnowflakeIdWorker idWorker;

    @Override
    public PageResult<UserVO> listUsers(int page, int size, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        List<UserVO> voList = result.getRecords().stream().map(this::toVO).toList();

        return new PageResult<>(voList, result.getTotal(), page, size);
    }

    @Override
    public UserVO createUser(String username, String password, String nickname, String role) {
        if (!RoleEnum.isValid(role)) {
            throw new ClientException("400", "无效的角色: " + role);
        }
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
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);

        log.info("Admin created user: id={}, username={}, role={}", user.getId(), username, role);
        return toVO(user);
    }

    @Override
    public UserVO updateUser(Long id, String nickname, String role, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ClientException("404", "用户不存在");
        }
        if (nickname != null) user.setNickname(nickname);
        if (role != null) {
            if (!RoleEnum.isValid(role)) {
                throw new ClientException("400", "无效的角色: " + role);
            }
            user.setRole(role);
        }
        if (status != null) user.setStatus(status);
        userMapper.updateById(user);

        log.info("Admin updated user: id={}, role={}, status={}", id, user.getRole(), user.getStatus());
        return toVO(user);
    }

    @Override
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ClientException("404", "用户不存在");
        }
        userMapper.deleteById(id);
        log.info("Admin deleted user: id={}, username={}", id, user.getUsername());
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

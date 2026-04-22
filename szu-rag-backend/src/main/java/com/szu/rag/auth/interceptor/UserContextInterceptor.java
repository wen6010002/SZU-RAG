package com.szu.rag.auth.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.szu.rag.auth.mapper.UserMapper;
import com.szu.rag.auth.model.entity.User;
import com.szu.rag.framework.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            if (StpUtil.isLogin()) {
                long userId = StpUtil.getLoginIdAsLong();
                User user = userMapper.selectById(userId);
                if (user != null) {
                    UserContext.LoginUser loginUser = new UserContext.LoginUser();
                    loginUser.setId(user.getId());
                    loginUser.setUsername(user.getUsername());
                    loginUser.setRole(user.getRole());
                    UserContext.set(loginUser);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to populate UserContext: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}

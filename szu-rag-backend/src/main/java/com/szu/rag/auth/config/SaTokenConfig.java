package com.szu.rag.auth.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.szu.rag.auth.interceptor.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer, StpInterface {

    private final UserContextInterceptor userContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Sa-Token 认证拦截器
        registry.addInterceptor(new SaInterceptor(handle -> {
            try {
                // 公开路径：auth 端点 + health
                SaRouter.match("/api/v1/auth/**").stop();
                SaRouter.match("/health").stop();

                // Admin 端点：需要 ADMIN 角色
                SaRouter.match("/api/v1/admin/**")
                        .check(r -> StpUtil.checkRole("ADMIN"));

                // 其他 /api/v1/** 端点：需要登录
                SaRouter.match("/api/v1/**")
                        .check(r -> StpUtil.checkLogin());
            } catch (SaTokenContextException e) {
                // SSE/异步请求中 SaTokenContext 可能未初始化，通过 RequestContextHolder 手动验证
                var reqAttrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (reqAttrs == null) return;
                var request = ((org.springframework.web.context.request.ServletRequestAttributes) reqAttrs).getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String tokenValue = authHeader.substring(7);
                    Object loginId = StpUtil.getLoginIdByToken(tokenValue);
                    if (loginId == null) StpUtil.checkLogin();
                } else {
                    StpUtil.checkLogin();
                }
            }
        })).addPathPatterns("/api/**");

        // UserContext 填充拦截器（在 Sa-Token 之后）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/**", "/health");
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从 Sa-Token 会话获取角色
        String role = (String) StpUtil.getSessionByLoginId(loginId).get("role");
        return role != null ? List.of(role) : Collections.emptyList();
    }
}

package com.szu.rag.framework.context;

import lombok.Data;

public class UserContext {
    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user != null ? user.getId() : null;
    }

    @Data
    public static class LoginUser {
        private Long id;
        private String username;
        private String role;
    }
}

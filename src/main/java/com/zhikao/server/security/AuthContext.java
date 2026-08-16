package com.zhikao.server.security;

/**
 * 当前登录用户上下文（由 JwtAuthenticationFilter 填充）。
 * 经 ThreadLocal 传递，请求结束后清理。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_TYPE = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Long userId, Integer userType) {
        USER_ID.set(userId);
        USER_TYPE.set(userType);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Integer getUserType() {
        return USER_TYPE.get();
    }

    public static void clear() {
        USER_ID.remove();
        USER_TYPE.remove();
    }
}

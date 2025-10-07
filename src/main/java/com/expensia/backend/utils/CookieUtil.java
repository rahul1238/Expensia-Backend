package com.expensia.backend.utils;

import jakarta.servlet.http.Cookie;

public final class CookieUtil {
    private CookieUtil() {}

    public static Cookie authCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // Configure cross-site behavior so frontend (different origin) can receive cookies.
        // Defaults assume production Cloud Run (HTTPS) with a separate frontend domain.
        boolean secure = Boolean.parseBoolean(System.getenv().getOrDefault("COOKIE_SECURE", "true"));
        String sameSite = System.getenv().getOrDefault("COOKIE_SAMESITE", "None");
        // Valid values: None, Lax, Strict. We default to None for cross-site credentialed requests.
        if (secure) cookie.setSecure(true);
        try {
            // setAttribute available in Servlet 6 (Jakarta). If not, it will just ignore.
            cookie.setAttribute("SameSite", sameSite);
        } catch (Throwable ignored) { }
        return cookie;
    }
}

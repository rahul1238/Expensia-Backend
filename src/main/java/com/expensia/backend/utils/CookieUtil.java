package com.expensia.backend.utils;

import jakarta.servlet.http.Cookie;

public final class CookieUtil {
    private CookieUtil() {}

    public static Cookie authCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // In production consider: cookie.setSecure(true); cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}

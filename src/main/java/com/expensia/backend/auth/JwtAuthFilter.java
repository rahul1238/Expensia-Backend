package com.expensia.backend.auth;

import com.expensia.backend.auth.service.JWTService;
import com.expensia.backend.provider.CustomUserDetailService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  @Autowired
  private JWTService jwtService;

  @Autowired
  private CustomUserDetailService userDetailService;

  private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
  private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    // Skip auth filter for authentication endpoints
    String path = request.getRequestURI();
    if (path.contains("/api/auth/login") || path.contains("/api/auth/register") || path.contains("/api/auth/refresh")) {
      filterChain.doFilter(request, response);
      return;
    }

    String jwt = extractJwtFromCookies(request);

    if (jwt == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String userEmail = jwtService.extractEmail(jwt);

      if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = userDetailService.loadUserByUsername(userEmail);

        if (jwtService.validateToken(jwt, userEmail)) {
          UsernamePasswordAuthenticationToken authToken =
              new UsernamePasswordAuthenticationToken(
                  userDetails,
                  null,
                  userDetails.getAuthorities()
              );

          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      }
    } catch (Exception e) {
      logger.error("Error processing JWT token", e);
      
      // Clear invalid auth tokens from cookies when validation fails
      if (!path.contains("/api/auth/logout")) {
        clearAuthCookies(response);
      }
    }

    filterChain.doFilter(request, response);
  }

  private String extractJwtFromCookies(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    // First try to get the access token
    Optional<Cookie> accessTokenCookie = Arrays.stream(cookies)
        .filter(cookie -> ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()))
        .findFirst();

    if (accessTokenCookie.isPresent()) {
      return accessTokenCookie.get().getValue();
    }

    // If access token is not present, check for refresh token as fallback
    return Arrays.stream(cookies)
        .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
        .findFirst()
        .map(Cookie::getValue)
        .orElse(null);
  }
  
  private void clearAuthCookies(HttpServletResponse response) {
    // Clear access token cookie
    Cookie accessTokenCookie = new Cookie(ACCESS_TOKEN_COOKIE_NAME, null);
    accessTokenCookie.setHttpOnly(true);
    accessTokenCookie.setPath("/");
    accessTokenCookie.setMaxAge(0);
    response.addCookie(accessTokenCookie);
    
    // Clear refresh token cookie
    Cookie refreshTokenCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
    refreshTokenCookie.setHttpOnly(true);
    refreshTokenCookie.setPath("/");
    refreshTokenCookie.setMaxAge(0);
    response.addCookie(refreshTokenCookie);
  }
}

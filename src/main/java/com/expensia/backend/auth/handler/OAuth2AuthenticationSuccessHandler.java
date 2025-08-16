package com.expensia.backend.auth.handler;

import com.expensia.backend.auth.service.CustomOAuth2User;
import com.expensia.backend.auth.service.JWTService;
import com.expensia.backend.auth.service.AuthService;
import org.springframework.context.annotation.Lazy;
import com.expensia.backend.utils.CookieUtil;
import com.expensia.backend.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired private JWTService jwtService;
    @Autowired @Lazy private AuthService authService;

    @Value("${app.oauth2.authorized-redirect-uris:http://localhost:5173/auth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) {
        
        CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oauth2User.getUser();

    String accessToken = jwtService.generateAccessToken(user.getEmail());
    String refreshToken = jwtService.generateRefreshToken(user.getEmail());
    authService.saveToken(user, accessToken, refreshToken);
    response.addCookie(CookieUtil.authCookie("accessToken", accessToken, 86400));
    response.addCookie(CookieUtil.authCookie("refreshToken", refreshToken, 604800));

        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("success", "true")
                .build().toUriString();
    }

}

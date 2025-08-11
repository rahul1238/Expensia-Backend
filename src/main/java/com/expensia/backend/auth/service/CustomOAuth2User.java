package com.expensia.backend.auth.service;

import com.expensia.backend.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public class CustomOAuth2User implements OAuth2User {
    
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;
    private final User user;

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.get(nameAttributeKey));
    }
}

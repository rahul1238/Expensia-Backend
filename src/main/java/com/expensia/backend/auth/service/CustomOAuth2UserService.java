package com.expensia.backend.auth.service;

import com.expensia.backend.model.User;
import com.expensia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        try {
            return processOAuth2User(userRequest, oauth2User);
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException("Error processing OAuth2 user");
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        String email = (String) attributes.get("email");
        String providerId = (String) attributes.get("sub");
        String firstName = (String) attributes.get("given_name");
        String lastName = (String) attributes.get("family_name");
        String profilePictureUrl = (String) attributes.get("picture");
        Boolean emailVerified = (Boolean) attributes.get("email_verified");
        
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();

            if (!registrationId.equals(user.getProvider())) {
                user.setProvider(registrationId);
                user.setProviderId(providerId);
                user.setProfilePictureUrl(profilePictureUrl);
                user.setEmailVerified(emailVerified != null ? emailVerified : false);
                user = userRepository.save(user);
            }
        } else {
            user = User.builder()
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .username(email) // Use email as username for OAuth2 users
                    .provider(registrationId)
                    .providerId(providerId)
                    .profilePictureUrl(profilePictureUrl)
                    .emailVerified(emailVerified != null ? emailVerified : false)
                    .password("")
                    .build();
            
            user = userRepository.save(user);
            log.info("Created new user from OAuth2 provider: {}", email);
        }

        return new CustomOAuth2User(oauth2User.getAttributes(), oauth2User.getName(), user);
    }
}

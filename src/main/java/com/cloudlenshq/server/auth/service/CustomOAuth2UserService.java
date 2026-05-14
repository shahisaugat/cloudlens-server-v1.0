package com.cloudlenshq.server.auth.service;

import com.cloudlenshq.server.auth.entity.Role;
import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String accessToken = userRequest.getAccessToken().getTokenValue();
        return processOAuth2User(provider, accessToken, oAuth2User);
    }

    private OAuth2User processOAuth2User(String provider, String accessToken, OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            // Some providers might not return email directly in attributes (like GitHub if not public)
            // For now assume it's there or handle accordingly
            email = oAuth2User.getAttribute("login") + "@github.com";
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Update existing user info if needed
            user.setFullName(oAuth2User.getAttribute("name"));
            user.setAvatarUrl(oAuth2User.getAttribute("avatar_url"));
            user.setGithubAccessToken(accessToken);
        } else {
            user = User.builder()
                    .email(email)
                    .fullName(oAuth2User.getAttribute("name"))
                    .role(Role.USER)
                    .provider(provider)
                    .providerId(oAuth2User.getName()) // Sub/ID from provider
                    .avatarUrl(oAuth2User.getAttribute("avatar_url"))
                    .githubAccessToken(accessToken)
                    .password("") // OAuth users don't have local password
                    .build();
        }
        userRepository.save(user);
        log.info("Processed OAuth2 user: {} (Provider: {})", email, provider);
        
        return oAuth2User;
    }
}

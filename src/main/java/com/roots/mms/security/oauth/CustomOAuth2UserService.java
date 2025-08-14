package com.roots.mms.security.oauth;

import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private static AuthProvider providerOf(String registrationId) {
        if ("google".equalsIgnoreCase(registrationId)) return AuthProvider.GOOGLE;
        if ("apple".equalsIgnoreCase(registrationId)) return AuthProvider.APPLE;
        return AuthProvider.LOCAL;
    }

    private static String extractEmail(String registrationId, Map<String, Object> attributes) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return (String) attributes.get("email");
        } else if ("apple".equalsIgnoreCase(registrationId)) {
            return (String) attributes.get("email");
        }
        return null;
    }

    private static String extractName(String registrationId, Map<String, Object> attributes) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return (String) attributes.getOrDefault("name", attributes.get("given_name"));
        } else if ("apple".equalsIgnoreCase(registrationId)) {
            return (String) attributes.get("name");
        }
        return null;
    }

    private static String extractSubject(String registrationId, Map<String, Object> attributes) {
        Object sub = attributes.get("sub");
        return sub != null ? sub.toString() : null;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = extractEmail(registrationId, attributes);
        String name = extractName(registrationId, attributes);
        String sub = extractSubject(registrationId, attributes);

        if (email == null) {
            throw new IllegalStateException("Email not provided by " + registrationId);
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> provisionUser(email, name, registrationId, sub));
        if (user.getProvider() == AuthProvider.LOCAL) {
            user.setProvider(providerOf(registrationId));
            user.setProviderId(sub);
            userRepository.save(user);
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r.getName().name())));

        Map<String, Object> principalAttrs = new HashMap<>(attributes);
        principalAttrs.put("userId", user.getId());
        return new DefaultOAuth2User(authorities, principalAttrs, "sub");
    }

    private User provisionUser(String email, String name, String registrationId, String sub) {
        String[] parts = Optional.ofNullable(name).orElse(email).split(" ");
        String first = parts.length > 0 ? parts[0] : "";
        String last = parts.length > 1 ? parts[parts.length - 1] : "";
        User u = new User(email, email, "", first, last);
        u.setActive(true);
        u.setProvider(providerOf(registrationId));
        u.setProviderId(sub);
        Role baseRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_USER));
        u.getRoles().add(baseRole);
        return userRepository.save(u);
    }
}


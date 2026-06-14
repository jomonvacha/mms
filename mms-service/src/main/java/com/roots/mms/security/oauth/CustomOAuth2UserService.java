package com.roots.mms.security.oauth;

import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.service.MemberService;
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
    private final MemberService memberService;

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
            throw new IllegalStateException("Unable to retrieve email from your authentication provider.");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> provisionUser(email, name, registrationId, sub));
        // When an existing user signs in via OAuth, update their provider to reflect
        // the current sign-in method. If they disconnected earlier and want to stay
        // LOCAL, they should not sign in via Google again — the disconnect flow
        // explains this. This ensures the Account tab always shows the correct state.
        if (user.getProvider() != providerOf(registrationId)) {
            user.setProvider(providerOf(registrationId));
            user.setProviderId(sub);
            userRepository.save(user);
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r.getName())));

        Map<String, Object> principalAttrs = new HashMap<>(attributes);
        // Stringify the UUID for downstream consumers (MDC, JWT extensions).
        principalAttrs.put("userId", user.getId() != null ? user.getId().toString() : null);
        // Use email as the principal name to align with local lookup
        if (email != null) {
            principalAttrs.putIfAbsent("email", email);
            return new DefaultOAuth2User(authorities, principalAttrs, "email");
        }
        return new DefaultOAuth2User(authorities, principalAttrs, "sub");
    }

    private User provisionUser(String email, String name, String registrationId, String sub) {
        String[] parts = Optional.ofNullable(name).orElse(email).split(" ");
        String first = parts.length > 0 ? parts[0] : "";
        String last = parts.length > 1 ? parts[parts.length - 1] : "";
        // Federated users have no local password — store null so hasPassword() reports false
        User u = new User(email, email, null, first, last);
        u.setActive(true);
        u.setProvider(providerOf(registrationId));
        u.setProviderId(sub);
        Role baseRole = roleRepository.findByName(ERole.ROLE_MEMBER.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", ERole.ROLE_MEMBER));
        u.getRoles().add(baseRole);
        User saved = userRepository.save(u);
        try { memberService.ensureDefaultMembership(saved); } catch (Exception e) {
            log.warn("Failed to assign default membership for OAuth user {}: {}", email, e.getMessage());
        }
        return saved;
    }
}

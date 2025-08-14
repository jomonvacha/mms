package com.roots.mms.security.oauth;

import com.roots.mms.security.jwt.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String username = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User ou) {
            Object emailAttr = ou.getAttributes().get("email");
            if (emailAttr != null) {
                username = emailAttr.toString();
            }
        }
        if (username == null && principal instanceof UserDetails ud) {
            username = ud.getUsername();
        }
        if (username == null) {
            username = authentication.getName();
        }

        String accessToken = jwtUtils.generateTokenFromUsername(username);
        String refreshToken = jwtUtils.generateRefreshTokenFromUsername(username);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        log.info("OAuth2 success for user={}, redirecting to frontend", username);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

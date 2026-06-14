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

        // Check for a cookie set by the frontend indicating where to return.
        // This lets multiple frontends (roots-ui on 3000, roots-mms/ui on 3001)
        // share the same OAuth2 flow and each get redirected back correctly.
        String returnTo = redirectUri;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("oauth2_return".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    returnTo = cookie.getValue();
                    // Clear the cookie
                    var clear = new jakarta.servlet.http.Cookie("oauth2_return", "");
                    clear.setMaxAge(0);
                    clear.setPath("/");
                    response.addCookie(clear);
                    break;
                }
            }
        }

        String targetUrl = UriComponentsBuilder.fromUriString(returnTo)
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        log.info("OAuth2 success for user={}, redirecting to {}", username, returnTo);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

package com.roots.mms.security.jwt;

import com.roots.mms.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

  private final JwtUtils jwtUtils;
  private final UserDetailsServiceImpl userDetailsService;
  private final TokenBlacklistService tokenBlacklist;
  private final com.roots.mms.service.SessionService sessionService;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
    throws ServletException, IOException {
    try {
      String jwt = parseJwt(request);
      if (jwt != null && jwtUtils.validateJwtToken(jwt) && !tokenBlacklist.isRevoked(jwt)
          && sessionValid(jwt)) {
        String username = jwtUtils.getUserNameFromJwtToken(jwt);

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userDetails,
            null,
            userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Add userId to MDC for downstream logs
        if (userDetails instanceof com.roots.mms.security.services.UserPrincipal up) {
          MDC.put("userId", String.valueOf(up.getId()));
        }
      }
    } catch (Exception e) {
      log.error("Cannot set user authentication: {}", e.getMessage(), e);
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Tokens minted before sessions existed (and OAuth redirect tokens) carry no
   * {@code sid} claim — those are accepted as before. Tokens that DO carry a
   * {@code sid} are only honoured while that session is still active, so a
   * remote "sign out" invalidates them immediately.
   */
  private boolean sessionValid(String jwt) {
    String sid = jwtUtils.getSessionId(jwt);
    return sid == null || sessionService.isActive(sid);
  }

  private String parseJwt(HttpServletRequest request) {
    String headerAuth = request.getHeader("Authorization");

    if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
      return headerAuth.substring(7);
    }

    return null;
  }
}

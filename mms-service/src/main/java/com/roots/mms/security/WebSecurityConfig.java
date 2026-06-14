package com.roots.mms.security;

import com.roots.mms.security.jwt.AuthEntryPointJwt;
import com.roots.mms.security.jwt.AuthTokenFilter;
import com.roots.mms.security.jwt.JwtUtils;
import com.roots.mms.security.jwt.TokenBlacklistService;
import com.roots.mms.security.oauth.CustomOAuth2UserService;
import com.roots.mms.security.oauth.OAuth2AuthenticationSuccessHandler;
import com.roots.mms.security.ratelimit.AuthRateLimitFilter;
import com.roots.mms.security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

  private final UserDetailsServiceImpl userDetailsService;
  private final AuthEntryPointJwt unauthorizedHandler;
  private final JwtUtils jwtUtils;
  private final TokenBlacklistService tokenBlacklist;
  private final AuthRateLimitFilter authRateLimitFilter;
  private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
  private final ObjectProvider<CustomOAuth2UserService> customOAuth2UserServiceProvider;
  private final ObjectProvider<OAuth2AuthenticationSuccessHandler> oAuth2AuthenticationSuccessHandlerProvider;

  /**
   * Comma-separated list of allowed CORS origins. Defaults to the local UI ports.
   * In production, set APP_CORS_ALLOWED_ORIGINS to your real frontend origin(s).
   */
  @Value("${app.cors.allowed-origins:http://localhost:3001,http://localhost:3000}")
  private String allowedOrigins;

  @Bean
  public AuthTokenFilter authenticationJwtTokenFilter() {
    return new AuthTokenFilter(jwtUtils, userDetailsService, tokenBlacklist);
  }

  /**
   * Centralized CORS configuration. Replaces the wide-open `@CrossOrigin(origins="*")`
   * sprinkled across controllers. Driven by the `app.cors.allowed-origins` property
   * so prod can lock it down to the real frontend origin without code changes.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    cfg.setAllowedOrigins(origins);
    cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }


  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }


  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(AbstractHttpConfigurer::disable)
      .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth ->
        auth.requestMatchers("/api/auth/**").permitAll()
          .requestMatchers("/api/test/**").permitAll()
          // Public service-info endpoints (version/health JSON for unauth clients)
          .requestMatchers("/api/info", "/api/health").permitAll()
          // Public locale/config endpoints (read-only, needed by all UIs)
          .requestMatchers("/api/locale/**").permitAll()
          // Public enforcement-rules endpoints (read-only config data)
          .requestMatchers("/api/enforcement-rules/**").permitAll()
          // Actuator: health/info public for liveness probes, everything else admin-only
          .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
          .requestMatchers("/actuator/**").hasRole("ADMIN")
          // OpenAPI / Swagger UI
          .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
          .requestMatchers("/h2-console/**").permitAll()
          .requestMatchers("/oauth2/callback-dev").permitAll()
          .requestMatchers("/api/auth/google-id-token").permitAll()
          .anyRequest().authenticated()
      );

    // Rate-limit sensitive auth endpoints BEFORE the JWT filter, so brute-force attempts
    // never reach the authentication manager.
    http.addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

    // Conditionally enable oauth2Login only if client registrations are present (tests can skip env vars)
    ClientRegistrationRepository repo = clientRegistrationRepositoryProvider.getIfAvailable();
    if (repo instanceof Iterable<?> iterable && iterable.iterator().hasNext()) {
      CustomOAuth2UserService oAuth2UserService = customOAuth2UserServiceProvider.getIfAvailable();
      OAuth2AuthenticationSuccessHandler successHandler = oAuth2AuthenticationSuccessHandlerProvider.getIfAvailable();
      if (oAuth2UserService != null && successHandler != null) {
        http.oauth2Login(oauth -> oauth
          .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
          .successHandler(successHandler)
        );
      }
    }

    return http.build();
  }
}

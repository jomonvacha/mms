package com.roots.mms.security.jwt;

import com.roots.mms.security.services.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.expiration}")
  private int jwtExpirationMs;

  @Value("${app.jwt.refresh-expiration}")
  private int jwtRefreshExpirationMs;

  public String generateJwtToken(Authentication authentication) {
    UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

    return Jwts.builder()
      .subject((userPrincipal.getUsername()))
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
      .signWith(key())
      .compact();
  }

  public String generateRefreshToken(Authentication authentication) {
    UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

    return Jwts.builder()
      .subject((userPrincipal.getUsername()))
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs))
      .signWith(key())
      .compact();
  }

  private SecretKey key() {
    try {
      return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    } catch (Exception e) {
      // Fallback to raw string bytes if not Base64-encoded or decoding fails
      return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
  }

  public String getUserNameFromJwtToken(String token) {
    return Jwts.parser().verifyWith(key()).build()
      .parseSignedClaims(token).getPayload().getSubject();
  }

  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
      return true;
    } catch (MalformedJwtException e) {
      log.error("Invalid JWT token: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      log.error("JWT token is expired: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.error("JWT token is unsupported: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.error("JWT claims string is empty: {}", e.getMessage());
    }

    return false;
  }

  public String generateTokenFromUsername(String username) {
    return Jwts.builder()
      .subject(username)
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
      .signWith(key())
      .compact();
  }

  public String generateRefreshTokenFromUsername(String username) {
    return Jwts.builder()
      .subject(username)
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs))
      .signWith(key())
      .compact();
  }
}

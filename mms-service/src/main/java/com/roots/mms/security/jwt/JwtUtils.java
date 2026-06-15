package com.roots.mms.security.jwt;

import com.roots.mms.security.services.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
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

  /**
   * Returns the expiration timestamp of the given token in epoch-millis.
   * Used by the signout endpoint so the blacklist can self-clean entries
   * once the token would have expired anyway.
   */
  public long getExpirationEpochMs(String token) {
    return Jwts.parser().verifyWith(key()).build()
      .parseSignedClaims(token).getPayload().getExpiration().getTime();
  }

  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
      return true;
    } catch (SecurityException e) {
      log.error("JWT signature validation failed: {}", e.getMessage());
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
    return generateTokenFromUsername(username, null);
  }

  public String generateRefreshTokenFromUsername(String username) {
    return generateRefreshTokenFromUsername(username, null);
  }

  /**
   * Access token carrying a {@code sid} (session id) claim. The filter uses
   * {@code sid} to enforce remote session revocation; refresh uses it to find
   * and rotate the owning session record.
   */
  public String generateTokenFromUsername(String username, String sessionId) {
    var builder = Jwts.builder()
      .subject(username)
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtExpirationMs));
    if (sessionId != null) builder.claim("sid", sessionId);
    return builder.signWith(key()).compact();
  }

  public String generateRefreshTokenFromUsername(String username, String sessionId) {
    var builder = Jwts.builder()
      .subject(username)
      .issuedAt(new Date())
      .expiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs));
    if (sessionId != null) builder.claim("sid", sessionId);
    return builder.signWith(key()).compact();
  }

  /** Returns the {@code sid} session-id claim, or null if the token has none. */
  public String getSessionId(String token) {
    try {
      Object sid = Jwts.parser().verifyWith(key()).build()
        .parseSignedClaims(token).getPayload().get("sid");
      return sid != null ? sid.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  public long refreshExpirationMs() {
    return jwtRefreshExpirationMs;
  }
}

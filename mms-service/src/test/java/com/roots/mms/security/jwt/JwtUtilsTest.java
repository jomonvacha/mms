package com.roots.mms.security.jwt;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.security.services.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String JWT_SECRET = "testSecretKey123456789012345678901234567890123456789012345678";
    private static final int JWT_EXPIRATION_MS = 3_600_000;
    private static final int JWT_REFRESH_EXPIRATION_MS = 7_200_000;

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", JWT_EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtUtils, "jwtRefreshExpirationMs", JWT_REFRESH_EXPIRATION_MS);
    }

    private Authentication authFor(String username) {
        User user = new User(username, username + "@test.com", "pw", "First", "Last");
        user.setId(UUID.randomUUID());
        user.setActive(true);
        Role role = new Role(ERole.ROLE_MEMBER);
        user.setRoles(Set.of(role));
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void generateJwtToken_returnsNonNullToken() {
        String token = jwtUtils.generateJwtToken(authFor("alice"));

        assertThat(token).isNotBlank();
    }

    @Test
    void generateJwtToken_subjectIsUsername() {
        String token = jwtUtils.generateJwtToken(authFor("alice"));

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("alice");
    }

    @Test
    void generateRefreshToken_subjectIsUsername() {
        String token = jwtUtils.generateRefreshToken(authFor("bob"));

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("bob");
    }

    @Test
    void generateTokenFromUsername_parseable() {
        String token = jwtUtils.generateTokenFromUsername("charlie");

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("charlie");
    }

    @Test
    void generateRefreshTokenFromUsername_parseable() {
        String token = jwtUtils.generateRefreshTokenFromUsername("dana");

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("dana");
    }

    @Test
    void validateJwtToken_validToken_returnsTrue() {
        String token = jwtUtils.generateTokenFromUsername("eve");

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void validateJwtToken_malformedToken_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateJwtToken_emptyString_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void validateJwtToken_expiredToken_returnsFalse() {
        // Build with the same key derivation as JwtUtils (raw-bytes fallback path since
        // JWT_SECRET is not valid Base64 when fed to Decoders.BASE64).
        String expired = Jwts.builder()
                .subject("frank")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .compact();

        assertThat(jwtUtils.validateJwtToken(expired)).isFalse();
    }

    @Test
    void validateJwtToken_wrongSignature_returnsFalse() {
        byte[] wrongKeyBytes = new byte[48];
        String badSig = Jwts.builder()
                .subject("grace")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(wrongKeyBytes), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtUtils.validateJwtToken(badSig)).isFalse();
    }

    @Test
    void getExpirationEpochMs_isFutureTimestamp() {
        String token = jwtUtils.generateTokenFromUsername("henry");
        long expiry = jwtUtils.getExpirationEpochMs(token);

        assertThat(expiry).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void getExpirationEpochMs_refreshTokenHasLongerExpiry() {
        String access = jwtUtils.generateTokenFromUsername("ivy");
        String refresh = jwtUtils.generateRefreshTokenFromUsername("ivy");

        assertThat(jwtUtils.getExpirationEpochMs(refresh))
                .isGreaterThan(jwtUtils.getExpirationEpochMs(access));
    }

    @Test
    void generateJwtToken_fallsBackToRawBytesWhenSecretIsNotBase64() {
        JwtUtils utils = new JwtUtils();
        // Must be >= 32 bytes so JJWT accepts it as an HMAC-SHA256 key.
        // Use characters that are clearly not valid Base64 to exercise the fallback path.
        ReflectionTestUtils.setField(utils, "jwtSecret", "plain!text!not!base64!!!ABCDEFGHIJKLMNO");
        ReflectionTestUtils.setField(utils, "jwtExpirationMs", 60_000);
        ReflectionTestUtils.setField(utils, "jwtRefreshExpirationMs", 120_000);

        String token = utils.generateTokenFromUsername("jack");

        assertThat(utils.getUserNameFromJwtToken(token)).isEqualTo("jack");
        assertThat(utils.validateJwtToken(token)).isTrue();
    }
}

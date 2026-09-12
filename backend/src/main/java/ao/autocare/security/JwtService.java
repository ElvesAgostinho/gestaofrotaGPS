package ao.autocare.security;

import ao.autocare.config.AutoCareProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public enum TokenType { ACCESS, REFRESH }

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AutoCareProperties props) {
        AutoCareProperties.Security.Jwt jwt = props.security().jwt();
        this.accessKey = Keys.hmacShaKeyFor(jwt.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(jwt.refreshSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = jwt.accessTtl();
        this.refreshTtl = jwt.refreshTtl();
    }

    public String generateAccessToken(String userId) {
        return build(userId, TokenType.ACCESS, accessKey, accessTtl);
    }

    public String generateRefreshToken(String userId) {
        return build(userId, TokenType.REFRESH, refreshKey, refreshTtl);
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /** Devolve o {@code sub} (id do utilizador) se o token de acesso for válido, senão null. */
    public String parseAccessSubject(String token) {
        return parseSubject(token, accessKey, TokenType.ACCESS);
    }

    public String parseRefreshSubject(String token) {
        return parseSubject(token, refreshKey, TokenType.REFRESH);
    }

    private String parseSubject(String token, SecretKey key, TokenType expected) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expected.name().equals(claims.get("type", String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private String build(String userId, TokenType type, SecretKey key, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}

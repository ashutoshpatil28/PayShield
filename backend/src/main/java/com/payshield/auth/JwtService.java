package com.payshield.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${payshield.security.jwt-secret}") String secret,
            @Value("${payshield.security.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("payshield.security.jwt-secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String issueAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenTtlSeconds);
        List<String> roleNames = user.getRoles().stream().map(RoleEntity::getName).sorted().toList();

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("roles", roleNames);

        if (user.getMerchantId() != null) {
            builder.claim("merchantId", user.getMerchantId().toString());
        }
        return builder.signWith(signingKey).compact();
    }

    public PayShieldUserPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String fullName = claims.get("fullName", String.class);
        String merchantIdRaw = claims.get("merchantId", String.class);
        UUID merchantId = merchantIdRaw != null ? UUID.fromString(merchantIdRaw) : null;

        @SuppressWarnings("unchecked")
        List<String> rolesList = claims.get("roles", List.class);
        Set<String> roles = rolesList != null ? Set.copyOf(rolesList) : Set.of();

        return new PayShieldUserPrincipal(userId, email, fullName, merchantId, roles, true);
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}

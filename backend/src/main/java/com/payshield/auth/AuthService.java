package com.payshield.auth;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthRateLimiter rateLimiter;
    private final long refreshTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            AuthRateLimiter rateLimiter,
            @Value("${payshield.security.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req, String ipAddress) {
        rateLimiter.checkAllowed(ipAddress + ":" + req.email().toLowerCase());

        UserEntity user = userRepository.findByEmailIgnoreCase(req.email().trim())
                .orElse(null);

        if (user == null || !user.isActive() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            auditService.record(
                    user != null ? user.getId() : null,
                    req.email(),
                    null,
                    "AUTH_LOGIN",
                    "SESSION",
                    null,
                    AuditOutcome.FAILURE,
                    ipAddress,
                    "{\"reason\":\"invalid_credentials_or_inactive\"}"
            );
            throw new DomainException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        String accessToken = jwtService.issueAccessToken(user);
        String rawRefreshToken = createAndStoreRefreshToken(user.getId());
        String rolesStr = user.getRoles().stream().map(RoleEntity::getName).sorted().collect(Collectors.joining(","));

        auditService.record(
                user.getId(),
                user.getEmail(),
                rolesStr,
                "AUTH_LOGIN",
                "SESSION",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                ipAddress,
                null
        );

        return new AuthDtos.TokenResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                toProfile(user)
        );
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest req, String ipAddress) {
        Instant now = Instant.now();
        String hash = sha256Hex(req.refreshToken());
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new DomainException("INVALID_REFRESH_TOKEN", "Refresh token is invalid", HttpStatus.UNAUTHORIZED));

        if (existing.isRevoked()) {
            // Reuse detection: revoke all active tokens for this user
            refreshTokenRepository.revokeAllActiveByUserId(existing.getUserId(), now);
            auditService.record(existing.getUserId(), null, null, "AUTH_REFRESH_REUSE_DETECTED",
                    "SESSION", existing.getId().toString(), AuditOutcome.DENIED, ipAddress, null);
            throw new DomainException("TOKEN_REVOKED", "Refresh token was already revoked", HttpStatus.UNAUTHORIZED);
        }

        if (existing.isExpired(now)) {
            throw new DomainException("TOKEN_EXPIRED", "Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = userRepository.findById(existing.getUserId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new DomainException("USER_INACTIVE", "User account is inactive", HttpStatus.UNAUTHORIZED));

        String newRawRefresh = generateOpaqueToken();
        UUID newTokenId = UUID.randomUUID();
        RefreshTokenEntity replacement = new RefreshTokenEntity(
                newTokenId,
                user.getId(),
                sha256Hex(newRawRefresh),
                now.plusSeconds(refreshTokenTtlSeconds),
                now
        );
        existing.revoke(newTokenId, now);
        refreshTokenRepository.save(existing);
        refreshTokenRepository.save(replacement);

        String accessToken = jwtService.issueAccessToken(user);
        return new AuthDtos.TokenResponse(
                accessToken,
                newRawRefresh,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                toProfile(user)
        );
    }

    @Transactional
    public void logout(PayShieldUserPrincipal principal, String ipAddress) {
        if (principal == null) {
            return;
        }
        refreshTokenRepository.revokeAllActiveByUserId(principal.getUserId(), Instant.now());
        auditService.record(
                principal.getUserId(),
                principal.getEmail(),
                String.join(",", principal.getRoles()),
                "AUTH_LOGOUT",
                "SESSION",
                principal.getUserId().toString(),
                AuditOutcome.SUCCESS,
                ipAddress,
                null
        );
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserProfileDto currentUserProfile(PayShieldUserPrincipal principal) {
        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        return toProfile(user);
    }

    private String createAndStoreRefreshToken(UUID userId) {
        Instant now = Instant.now();
        String raw = generateOpaqueToken();
        RefreshTokenEntity entity = new RefreshTokenEntity(
                UUID.randomUUID(),
                userId,
                sha256Hex(raw),
                now.plusSeconds(refreshTokenTtlSeconds),
                now
        );
        refreshTokenRepository.save(entity);
        return raw;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static AuthDtos.UserProfileDto toProfile(UserEntity u) {
        return new AuthDtos.UserProfileDto(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getMerchantId(),
                u.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()),
                u.isActive(),
                u.isDemoUser()
        );
    }
}

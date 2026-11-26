package com.payshield.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UserProfileDto user
    ) {}

    public record UserProfileDto(
            UUID id,
            String email,
            String fullName,
            UUID merchantId,
            Set<String> roles,
            boolean active,
            boolean demoUser
    ) {}

    public record UpdateUserRolesRequest(
            @NotEmpty Set<String> roles
    ) {}

    public record UpdateUserStatusRequest(
            boolean active
    ) {}

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotBlank String password,
            UUID merchantId,
            @NotEmpty Set<String> roles
    ) {}

    public record UserSummaryDto(
            UUID id,
            String email,
            String fullName,
            UUID merchantId,
            Set<String> roles,
            boolean active,
            boolean demoUser,
            Instant createdAt
    ) {}
}

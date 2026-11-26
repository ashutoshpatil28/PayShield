package com.payshield.auth;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class UserManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementController(UserRepository userRepository,
                                    RoleRepository roleRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuthDtos.UserSummaryDto> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(UserEntity::getEmail))
                .map(u -> new AuthDtos.UserSummaryDto(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getMerchantId(),
                        u.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()),
                        u.isActive(),
                        u.isDemoUser(),
                        u.getCreatedAt()
                ))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthDtos.UserSummaryDto createUser(
            @Valid @RequestBody AuthDtos.CreateUserRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        if (userRepository.existsByEmailIgnoreCase(req.email().trim())) {
            throw new DomainException("EMAIL_EXISTS", "User with this email already exists", HttpStatus.CONFLICT);
        }
        Set<RoleEntity> resolvedRoles = resolveRoles(req.roles());
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                req.email().trim().toLowerCase(),
                req.fullName().trim(),
                passwordEncoder.encode(req.password()),
                req.merchantId(),
                true,
                false,
                now,
                now
        );
        user.setRoles(resolvedRoles);
        userRepository.save(user);

        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "USER_CREATED", "USER", user.getId().toString(),
                AuditOutcome.SUCCESS, httpReq.getRemoteAddr(),
                "{\"roles\":\"" + String.join(",", req.roles()) + "\"}"
        );

        return new AuthDtos.UserSummaryDto(
                user.getId(), user.getEmail(), user.getFullName(), user.getMerchantId(),
                req.roles(), user.isActive(), user.isDemoUser(), user.getCreatedAt()
        );
    }

    @PutMapping("/{userId}/roles")
    @Transactional
    public AuthDtos.UserSummaryDto updateRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody AuthDtos.UpdateUserRolesRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        Set<RoleEntity> resolvedRoles = resolveRoles(req.roles());
        user.setRoles(resolvedRoles);
        userRepository.save(user);

        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "USER_ROLES_UPDATED", "USER", user.getId().toString(),
                AuditOutcome.SUCCESS, httpReq.getRemoteAddr(),
                "{\"newRoles\":\"" + String.join(",", req.roles()) + "\"}"
        );

        return new AuthDtos.UserSummaryDto(
                user.getId(), user.getEmail(), user.getFullName(), user.getMerchantId(),
                user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()),
                user.isActive(), user.isDemoUser(), user.getCreatedAt()
        );
    }

    @PatchMapping("/{userId}/status")
    @Transactional
    public AuthDtos.UserSummaryDto updateStatus(
            @PathVariable UUID userId,
            @RequestBody AuthDtos.UpdateUserStatusRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        user.setActive(req.active());
        userRepository.save(user);

        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "USER_STATUS_UPDATED", "USER", user.getId().toString(),
                AuditOutcome.SUCCESS, httpReq.getRemoteAddr(),
                "{\"active\":" + req.active() + "}"
        );

        return new AuthDtos.UserSummaryDto(
                user.getId(), user.getEmail(), user.getFullName(), user.getMerchantId(),
                user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()),
                user.isActive(), user.isDemoUser(), user.getCreatedAt()
        );
    }

    private Set<RoleEntity> resolveRoles(Set<String> roleNames) {
        Set<RoleEntity> resolved = new HashSet<>();
        for (String name : roleNames) {
            RoleEntity role = roleRepository.findByName(name)
                    .orElseThrow(() -> new DomainException("INVALID_ROLE", "Unknown role: " + name, HttpStatus.BAD_REQUEST));
            resolved.add(role);
        }
        return resolved;
    }
}

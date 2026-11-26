package com.payshield.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authService.refresh(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal PayShieldUserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        authService.logout(principal, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserProfileDto> me(
            @AuthenticationPrincipal PayShieldUserPrincipal principal
    ) {
        return ResponseEntity.ok(authService.currentUserProfile(principal));
    }
}

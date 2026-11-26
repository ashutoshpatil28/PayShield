package com.payshield.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PayShieldUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String fullName;
    private final UUID merchantId;
    private final Set<String> roles;
    private final boolean active;

    public PayShieldUserPrincipal(UUID userId, String email, String fullName,
                                  UUID merchantId, Set<String> roles, boolean active) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.merchantId = merchantId;
        this.roles = Set.copyOf(roles);
        this.active = active;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public UUID getMerchantId() { return merchantId; }
    public Set<String> getRoles() { return roles; }
    public boolean hasRole(RoleName role) { return roles.contains(role.name()); }
    public boolean isMerchantScoped() { return merchantId != null && !hasRole(RoleName.PLATFORM_ADMIN); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}

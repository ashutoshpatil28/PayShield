package com.payshield.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_demo_user", nullable = false)
    private boolean demoUser = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    protected UserEntity() {
    }

    public UserEntity(UUID id, String email, String fullName, String passwordHash,
                      UUID merchantId, boolean active, boolean demoUser,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.merchantId = merchantId;
        this.active = active;
        this.demoUser = demoUser;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public UUID getMerchantId() { return merchantId; }
    public boolean isActive() { return active; }
    public boolean isDemoUser() { return demoUser; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<RoleEntity> getRoles() { return roles; }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles;
        this.updatedAt = Instant.now();
    }
}

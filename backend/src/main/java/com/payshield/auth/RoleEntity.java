package com.payshield.auth;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(nullable = false, length = 255)
    private String description;

    protected RoleEntity() {
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}

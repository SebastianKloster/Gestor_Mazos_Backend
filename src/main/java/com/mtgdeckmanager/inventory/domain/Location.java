package com.mtgdeckmanager.inventory.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "locations")
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 16) private LocationType type;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Location() { }
    public Location(String name, LocationType type) {
        this.name = name; this.type = type;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public void update(String name, LocationType type) {
        this.name = name; this.type = type; this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public LocationType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

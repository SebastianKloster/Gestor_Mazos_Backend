package com.mtgdeckmanager.inventory.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "card_movements")
public class CardMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_copy_id", nullable = false, updatable = false) private CardCopy cardCopy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", updatable = false) private Location fromLocation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", updatable = false) private Location toLocation;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "from_zone", length = 16, updatable = false) private DeckZone fromZone;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "to_zone", length = 16, updatable = false) private DeckZone toZone;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "movement_type", nullable = false, length = 32, updatable = false)
    private MovementType movementType;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CardMovement() { }
    public CardMovement(CardCopy copy, Location from, DeckZone fromZone, Location to, DeckZone toZone, MovementType type) {
        this.cardCopy = copy; this.fromLocation = from; this.fromZone = fromZone;
        this.toLocation = to; this.toZone = toZone; this.movementType = type; this.createdAt = Instant.now();
    }
}

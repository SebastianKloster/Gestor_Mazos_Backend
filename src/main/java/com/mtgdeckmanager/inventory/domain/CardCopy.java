package com.mtgdeckmanager.inventory.domain;

import com.mtgdeckmanager.catalog.domain.Printing;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "card_copies")
public class CardCopy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printing_id", nullable = false) private Printing printing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false) private Location location;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "deck_zone", length = 16) private DeckZone deckZone;
    @Column(name = "is_proxy", nullable = false) private boolean proxy;
    @Column(name = "is_foil", nullable = false) private boolean foil;
    @Column(name = "archived_at") private Instant archivedAt;
    @Version @Column(nullable = false) private Long version;

    protected CardCopy() { }
    public CardCopy(Printing printing, Location location, DeckZone zone, boolean proxy, boolean foil) {
        this.printing = printing; this.location = location; this.deckZone = zone;
        this.proxy = proxy; this.foil = foil;
    }
    public void moveTo(Location location, DeckZone zone) { this.location = location; this.deckZone = zone; }
    public void updateFlags(boolean proxy, boolean foil) { this.proxy = proxy; this.foil = foil; }
    public void archive() { this.archivedAt = Instant.now(); }
    public Long getId() { return id; }
    public Printing getPrinting() { return printing; }
    public Location getLocation() { return location; }
    public DeckZone getDeckZone() { return deckZone; }
    public boolean isProxy() { return proxy; }
    public boolean isFoil() { return foil; }
    public Instant getArchivedAt() { return archivedAt; }
    public Long getVersion() { return version; }
}

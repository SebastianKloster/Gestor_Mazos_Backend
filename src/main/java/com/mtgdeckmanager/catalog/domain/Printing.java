package com.mtgdeckmanager.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "printings")
public class Printing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false) private Card card;
    @Column(name = "scryfall_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)") private String scryfallId;
    @Column(name = "set_code", nullable = false, length = 16) private String setCode;
    @Column(name = "set_name", nullable = false) private String setName;
    @Column(name = "collector_number", nullable = false, length = 64) private String collectorNumber;
    @Column(name = "image_url", length = 2048) private String imageUrl;
    @Column(name = "back_image_url", length = 2048) private String backImageUrl;
    @Column(name = "available_finishes", nullable = false, length = 128) private String availableFinishes;
    @Column(name = "usd_price", precision = 12, scale = 2) private BigDecimal usdPrice;
    @Column(name = "usd_foil_price", precision = 12, scale = 2) private BigDecimal usdFoilPrice;
    @Column(name = "eur_price", precision = 12, scale = 2) private BigDecimal eurPrice;
    @Column(name = "eur_foil_price", precision = 12, scale = 2) private BigDecimal eurFoilPrice;
    @Column(name = "price_updated_at", nullable = false) private Instant priceUpdatedAt;

    protected Printing() { }
    public Printing(Card card, String scryfallId) { this.card = card; this.scryfallId = scryfallId; }
    public void update(String setCode, String setName, String collectorNumber, String imageUrl,
            String backImageUrl, String finishes, BigDecimal usd, BigDecimal usdFoil,
            BigDecimal eur, BigDecimal eurFoil, Instant updatedAt) {
        this.setCode = setCode; this.setName = setName; this.collectorNumber = collectorNumber;
        this.imageUrl = imageUrl; this.backImageUrl = backImageUrl; this.availableFinishes = finishes;
        this.usdPrice = usd; this.usdFoilPrice = usdFoil; this.eurPrice = eur; this.eurFoilPrice = eurFoil;
        this.priceUpdatedAt = updatedAt;
    }
    public Long getId() { return id; }
    public Card getCard() { return card; }
    public String getScryfallId() { return scryfallId; }
    public String getSetCode() { return setCode; }
    public String getSetName() { return setName; }
    public String getCollectorNumber() { return collectorNumber; }
    public String getImageUrl() { return imageUrl; }
    public String getBackImageUrl() { return backImageUrl; }
    public String getAvailableFinishes() { return availableFinishes; }
    public BigDecimal getUsdPrice() { return usdPrice; }
    public BigDecimal getUsdFoilPrice() { return usdFoilPrice; }
    public BigDecimal getEurPrice() { return eurPrice; }
    public BigDecimal getEurFoilPrice() { return eurFoilPrice; }
    public Instant getPriceUpdatedAt() { return priceUpdatedAt; }
}

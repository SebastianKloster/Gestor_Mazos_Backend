package com.mtgdeckmanager.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cards")
public class Card {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "oracle_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)") private String oracleId;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 32) private String layout;
    @Column(name = "type_line", length = 512) private String typeLine;
    @Column(name = "mana_value", precision = 10, scale = 2) private BigDecimal manaValue;
    @Column(name = "oracle_text", columnDefinition = "TEXT") private String oracleText;
    @Column(name = "mana_cost") private String manaCost;
    @Column(name = "color_identity", nullable = false, length = 5) private String colorIdentity;
    @ElementCollection
    @CollectionTable(name = "card_faces", joinColumns = @JoinColumn(name = "card_id"))
    @OrderColumn(name = "face_order")
    private List<CardFace> faces = new ArrayList<>();

    protected Card() { }
    public Card(String oracleId) { this.oracleId = oracleId; }
    public void update(String name, String layout, String typeLine, BigDecimal manaValue,
            String oracleText, String manaCost, String colorIdentity, List<CardFace> faces) {
        this.name = name; this.layout = layout; this.typeLine = typeLine; this.manaValue = manaValue;
        this.oracleText = oracleText; this.manaCost = manaCost; this.colorIdentity = colorIdentity;
        this.faces.clear(); this.faces.addAll(faces);
    }
    public Long getId() { return id; }
    public String getOracleId() { return oracleId; }
    public String getName() { return name; }
    public String getLayout() { return layout; }
    public String getTypeLine() { return typeLine; }
    public BigDecimal getManaValue() { return manaValue; }
    public String getOracleText() { return oracleText; }
    public String getManaCost() { return manaCost; }
    public String getColorIdentity() { return colorIdentity; }
    public List<CardFace> getFaces() { return List.copyOf(faces); }
}

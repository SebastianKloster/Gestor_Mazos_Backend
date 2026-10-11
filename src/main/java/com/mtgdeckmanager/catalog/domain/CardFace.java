package com.mtgdeckmanager.catalog.domain;

import jakarta.persistence.*;

@Embeddable
public class CardFace {
    @Column(nullable = false) private String name;
    @Column(name = "type_line", length = 512) private String typeLine;
    @Column(name = "oracle_text", columnDefinition = "TEXT") private String oracleText;
    @Column(name = "mana_cost") private String manaCost;

    protected CardFace() { }
    public CardFace(String name, String typeLine, String oracleText, String manaCost) {
        this.name = name; this.typeLine = typeLine; this.oracleText = oracleText; this.manaCost = manaCost;
    }
    public String getName() { return name; }
    public String getTypeLine() { return typeLine; }
    public String getOracleText() { return oracleText; }
    public String getManaCost() { return manaCost; }
}

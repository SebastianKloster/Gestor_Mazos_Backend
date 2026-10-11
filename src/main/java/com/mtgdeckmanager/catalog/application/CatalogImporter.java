package com.mtgdeckmanager.catalog.application;

import com.mtgdeckmanager.catalog.api.CatalogDtos;
import com.mtgdeckmanager.catalog.domain.*;
import com.mtgdeckmanager.catalog.infrastructure.persistence.*;
import com.mtgdeckmanager.catalog.infrastructure.scryfall.ScryfallDtos.CardData;
import com.mtgdeckmanager.catalog.infrastructure.scryfall.ScryfallException;
import com.mtgdeckmanager.common.error.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogImporter {
    private final CardRepository cards;
    private final PrintingRepository printings;
    public CatalogImporter(CardRepository cards, PrintingRepository printings) {
        this.cards = cards; this.printings = printings;
    }
    public record Imported(CatalogDtos.CardSummary card, CatalogDtos.PrintingDetail printing) { }

    @Transactional
    public List<Imported> importCards(List<CardData> data, String expectedOracleId, Instant fetchedAt) {
        List<Imported> imported = new ArrayList<>();
        for (CardData d : data) {
            if (!"en".equals(d.lang())) throw new ScryfallException("Scryfall returned a non-English printing", false);
            if (d.oracleId() == null) throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This Scryfall layout has no root oracle_id and is not supported yet");
            if (expectedOracleId != null && !expectedOracleId.equals(d.oracleId().toString()))
                throw new ScryfallException("Scryfall returned a printing of a different card", false);
            if (d.id() == null || d.name() == null || d.layout() == null || d.setCode() == null
                    || d.setName() == null || d.collectorNumber() == null)
                throw new ScryfallException("Scryfall returned incomplete card data", false);
            var card = cards.findByOracleId(d.oracleId().toString()).orElseGet(() -> new Card(d.oracleId().toString()));
            var faces = d.faces() == null ? List.<CardFace>of() : d.faces().stream()
                    .map(f -> new CardFace(f.name(), f.typeLine(), f.oracleText(), f.manaCost())).toList();
            card.update(d.name(), d.layout(), d.typeLine(), d.cmc(), d.oracleText(), d.manaCost(),
                    d.colorIdentity() == null ? "" : String.join("", d.colorIdentity()), faces);
            card = cards.save(card);
            var printing = printings.findByScryfallId(d.id().toString()).orElse(null);
            if (printing == null) printing = new Printing(card, d.id().toString());
            else if (!printing.getCard().getOracleId().equals(card.getOracleId()))
                throw new ScryfallException("Scryfall printing identity changed", false);
            String front = image(d.images());
            String back = null;
            if (front == null && d.faces() != null && !d.faces().isEmpty()) {
                front = image(d.faces().getFirst().images());
                if (d.faces().size() > 1) back = image(d.faces().get(1).images());
            }
            if (printing.getPriceUpdatedAt() == null || !printing.getPriceUpdatedAt().isAfter(fetchedAt)) {
                printing.update(d.setCode(), d.setName(), d.collectorNumber(), front, back,
                        d.finishes() == null ? "" : String.join(",", d.finishes()),
                        price(d, "usd"), price(d, "usd_foil"), price(d, "eur"), price(d, "eur_foil"), fetchedAt);
            }
            printings.save(printing);
            imported.add(new Imported(CatalogDtos.summary(card), CatalogDtos.detail(printing)));
        }
        return List.copyOf(imported);
    }
    private static String image(Map<String, String> images) {
        if (images == null) return null;
        for (String key : List.of("normal", "png", "large")) {
            String url = images.get(key);
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }
    private static BigDecimal price(CardData d, String field) {
        if (d.prices() == null || d.prices().get(field) == null) return null;
        try {
            BigDecimal value = new BigDecimal(d.prices().get(field));
            if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 10)
                throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new ScryfallException("Scryfall returned an invalid price", false); }
    }
}

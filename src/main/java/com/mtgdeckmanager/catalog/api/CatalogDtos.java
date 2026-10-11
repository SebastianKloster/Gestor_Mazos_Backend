package com.mtgdeckmanager.catalog.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.mtgdeckmanager.catalog.domain.Card;
import com.mtgdeckmanager.catalog.domain.Printing;

public final class CatalogDtos {
    private CatalogDtos() { }
    public record Page<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) { }
    public record CardSummary(Long id, String oracleId, String name, String layout,
            String typeLine, BigDecimal manaValue) { }
    public record Face(String name, String typeLine, String oracleText, String manaCost) { }
    public record CardDetail(Long id, String oracleId, String name, String layout, String typeLine,
            BigDecimal manaValue, String oracleText, String manaCost, List<String> colorIdentity,
            List<Face> faces) { }
    public record PrintingDetail(Long id, Long cardId, String scryfallId, String setCode, String setName,
            String collectorNumber, String imageUrl, String backImageUrl, List<String> availableFinishes,
            BigDecimal usdPrice, BigDecimal usdFoilPrice, BigDecimal eurPrice, BigDecimal eurFoilPrice,
            Instant priceUpdatedAt) { }
    public static CardSummary summary(Card c) {
        return new CardSummary(c.getId(), c.getOracleId(), c.getName(), c.getLayout(), c.getTypeLine(), c.getManaValue());
    }
    public static CardDetail detail(Card c) {
        return new CardDetail(c.getId(), c.getOracleId(), c.getName(), c.getLayout(), c.getTypeLine(),
                c.getManaValue(), c.getOracleText(), c.getManaCost(),
                c.getColorIdentity().chars().mapToObj(v -> String.valueOf((char) v)).toList(),
                c.getFaces().stream().map(f -> new Face(f.getName(), f.getTypeLine(), f.getOracleText(), f.getManaCost())).toList());
    }
    public static PrintingDetail detail(Printing p) {
        return new PrintingDetail(p.getId(), p.getCard().getId(), p.getScryfallId(), p.getSetCode(),
                p.getSetName(), p.getCollectorNumber(), p.getImageUrl(), p.getBackImageUrl(),
                p.getAvailableFinishes().isEmpty() ? List.of() : List.of(p.getAvailableFinishes().split(",")),
                p.getUsdPrice(), p.getUsdFoilPrice(), p.getEurPrice(), p.getEurFoilPrice(), p.getPriceUpdatedAt());
    }
}

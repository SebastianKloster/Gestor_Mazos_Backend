package com.mtgdeckmanager.catalog.infrastructure.scryfall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScryfallDtos {
    private ScryfallDtos() { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardData(UUID id, @JsonProperty("oracle_id") UUID oracleId, String name,
            String lang, String layout, @JsonProperty("type_line") String typeLine, BigDecimal cmc,
            @JsonProperty("oracle_text") String oracleText, @JsonProperty("mana_cost") String manaCost,
            @JsonProperty("color_identity") List<String> colorIdentity,
            @JsonProperty("card_faces") List<FaceData> faces,
            @JsonProperty("image_uris") Map<String, String> images,
            @JsonProperty("set") String setCode, @JsonProperty("set_name") String setName,
            @JsonProperty("collector_number") String collectorNumber,
            List<String> finishes, Map<String, String> prices) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FaceData(String name, @JsonProperty("type_line") String typeLine,
            @JsonProperty("oracle_text") String oracleText, @JsonProperty("mana_cost") String manaCost,
            @JsonProperty("image_uris") Map<String, String> images) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(List<CardData> data, @JsonProperty("total_cards") long totalCards,
            @JsonProperty("has_more") boolean hasMore) { }
}

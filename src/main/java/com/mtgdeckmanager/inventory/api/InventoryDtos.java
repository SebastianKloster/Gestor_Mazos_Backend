package com.mtgdeckmanager.inventory.api;

import com.mtgdeckmanager.inventory.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() { }
    public record Page<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) { }
    public record CreateLocation(@NotBlank @Size(max = 255) String name, @NotNull LocationType type) { }
    public record PatchLocation(@Size(min = 1, max = 255) @Pattern(regexp = "(?s).*\\S.*") String name, LocationType type) { }
    public record LocationDetail(Long id, String name, LocationType type, Instant createdAt, Instant updatedAt) { }
    public record CreateCopies(@NotNull @Positive Long printingId, @NotNull @Positive Long locationId,
            DeckZone deckZone, @NotNull @Min(1) @Max(100) Integer quantity, Boolean isProxy, Boolean isFoil) { }
    public record MoveCopy(@NotNull @Positive Long toLocationId, DeckZone toZone,
            @NotNull @PositiveOrZero Long expectedVersion) { }
    public record PatchCopy(Boolean isProxy, Boolean isFoil, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record ArchiveCopy(@NotNull @PositiveOrZero Long expectedVersion) { }
    public record CopyDetail(Long id, Long cardId, String cardName, Long printingId, String scryfallId,
            String setCode, String collectorNumber, String imageUrl, String backImageUrl,
            Long locationId, String locationName, LocationType locationType, DeckZone deckZone,
            boolean isProxy, boolean isFoil, Instant archivedAt, Long version) { }
    public record LocationAvailability(Long locationId, String locationName, LocationType locationType,
            long quantity, long proxyQuantity) { }
    public record Availability(Long cardId, long ownedQuantity, long authenticQuantity, long proxyQuantity,
            long freeQuantity, long inDeckQuantity, List<LocationAvailability> locations) { }

    public static LocationDetail detail(Location location) {
        return new LocationDetail(location.getId(), location.getName(), location.getType(), location.getCreatedAt(), location.getUpdatedAt());
    }
    public static CopyDetail detail(CardCopy copy) {
        var p = copy.getPrinting(); var l = copy.getLocation(); var card = p.getCard();
        return new CopyDetail(copy.getId(), card.getId(), card.getName(), p.getId(), p.getScryfallId(),
                p.getSetCode(), p.getCollectorNumber(), p.getImageUrl(), p.getBackImageUrl(),
                l.getId(), l.getName(), l.getType(), copy.getDeckZone(), copy.isProxy(), copy.isFoil(),
                copy.getArchivedAt(), copy.getVersion());
    }
}

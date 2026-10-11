package com.mtgdeckmanager.inventory.application;

import com.mtgdeckmanager.inventory.api.InventoryDtos;
import com.mtgdeckmanager.inventory.domain.*;
import com.mtgdeckmanager.inventory.infrastructure.LocationRepository;
import com.mtgdeckmanager.common.error.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LocationService {
    private final LocationRepository locations;
    public LocationService(LocationRepository locations) { this.locations = locations; }
    public InventoryDtos.LocationDetail create(InventoryDtos.CreateLocation request) {
        requireNonDeck(request.type());
        return InventoryDtos.detail(locations.save(new Location(request.name().strip(), request.type())));
    }
    public InventoryDtos.LocationDetail update(long id, InventoryDtos.PatchLocation request) {
        Location location = get(id);
        requireNonDeck(location.getType());
        LocationType type = request.type() == null ? location.getType() : request.type();
        requireNonDeck(type);
        location.update(request.name() == null ? location.getName() : request.name().strip(), type);
        return InventoryDtos.detail(location);
    }
    @Transactional(readOnly = true)
    public InventoryDtos.Page<InventoryDtos.LocationDetail> list(int page, int size) {
        var result = locations.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new InventoryDtos.Page<>(result.getContent().stream().map(InventoryDtos::detail).toList(),
                page, size, result.getTotalElements(), result.hasNext());
    }
    public Location get(long id) {
        return locations.findById(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Location not found"));
    }
    private void requireNonDeck(LocationType type) {
        if (type == LocationType.DECK) throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,
                "DECK locations must be managed through a deck; deck management is not available yet");
    }
    public static void validateZone(Location location, DeckZone zone) {
        if ((location.getType() == LocationType.DECK) != (zone != null)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A deck location requires a zone; other locations require a null zone");
        }
    }
}

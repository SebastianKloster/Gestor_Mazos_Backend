package com.mtgdeckmanager.inventory.application;

import com.mtgdeckmanager.catalog.infrastructure.persistence.*;
import com.mtgdeckmanager.common.error.DomainException;
import com.mtgdeckmanager.inventory.api.InventoryDtos;
import com.mtgdeckmanager.inventory.domain.*;
import com.mtgdeckmanager.inventory.infrastructure.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {
    private final PrintingRepository printings;
    private final CardCopyRepository copies;
    private final CardMovementRepository movements;
    private final LocationService locations;
    public InventoryService(PrintingRepository printings, CardCopyRepository copies,
            CardMovementRepository movements, LocationService locations) {
        this.printings = printings; this.copies = copies; this.movements = movements; this.locations = locations;
    }
    public List<InventoryDtos.CopyDetail> acquire(InventoryDtos.CreateCopies request) {
        var printing = printings.findById(request.printingId()).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Printing not found"));
        var location = locations.get(request.locationId());
        LocationService.validateZone(location, request.deckZone());
        List<CardCopy> acquired = new ArrayList<>();
        for (int i = 0; i < request.quantity(); i++) {
            var copy = copies.save(new CardCopy(printing, location, request.deckZone(),
                    Boolean.TRUE.equals(request.isProxy()), Boolean.TRUE.equals(request.isFoil())));
            movements.save(new CardMovement(copy, null, null, location, request.deckZone(), MovementType.ACQUIRED));
            acquired.add(copy);
        }
        copies.flush();
        return acquired.stream().map(InventoryDtos::detail).toList();
    }
    public InventoryDtos.CopyDetail move(long id, InventoryDtos.MoveCopy request) {
        CardCopy copy = activeCopy(id, request.expectedVersion());
        var destination = locations.get(request.toLocationId());
        LocationService.validateZone(destination, request.toZone());
        if (copy.getLocation().getId().equals(destination.getId()) && copy.getDeckZone() == request.toZone())
            return InventoryDtos.detail(copy);
        var origin = copy.getLocation(); var originZone = copy.getDeckZone();
        copy.moveTo(destination, request.toZone());
        copies.flush(); // A stale write fails before recording the movement; both remain in the same transaction.
        movements.save(new CardMovement(copy, origin, originZone, destination, request.toZone(), MovementType.TRANSFERRED));
        return InventoryDtos.detail(copy);
    }
    public InventoryDtos.CopyDetail updateFlags(long id, InventoryDtos.PatchCopy request) {
        CardCopy copy = activeCopy(id, request.expectedVersion());
        copy.updateFlags(request.isProxy() == null ? copy.isProxy() : request.isProxy(),
                request.isFoil() == null ? copy.isFoil() : request.isFoil());
        copies.flush();
        return InventoryDtos.detail(copy);
    }
    public InventoryDtos.CopyDetail archive(long id, InventoryDtos.ArchiveCopy request) {
        CardCopy copy = activeCopy(id, request.expectedVersion());
        copy.archive();
        copies.flush();
        movements.save(new CardMovement(copy, copy.getLocation(), copy.getDeckZone(), null, null, MovementType.REMOVED_FROM_COLLECTION));
        return InventoryDtos.detail(copy);
    }
    private CardCopy activeCopy(long id, long expectedVersion) {
        CardCopy copy = copies.findById(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Card copy not found"));
        if (copy.getArchivedAt() != null) throw new DomainException(HttpStatus.CONFLICT, "The card copy is archived");
        if (copy.getVersion() != expectedVersion) throw new DomainException(HttpStatus.CONFLICT, "The card copy changed; reload its current version");
        return copy;
    }
}

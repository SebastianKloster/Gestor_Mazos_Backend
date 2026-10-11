package com.mtgdeckmanager.inventory.application;

import com.mtgdeckmanager.catalog.infrastructure.persistence.CardRepository;
import com.mtgdeckmanager.common.error.DomainException;
import com.mtgdeckmanager.inventory.api.InventoryDtos;
import com.mtgdeckmanager.inventory.domain.*;
import com.mtgdeckmanager.inventory.infrastructure.*;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryReader {
    private final CardCopyRepository copies;
    private final CardRepository cards;
    private final LocationRepository locations;
    public InventoryReader(CardCopyRepository copies, CardRepository cards, LocationRepository locations) {
        this.copies = copies; this.cards = cards; this.locations = locations;
    }
    public InventoryDtos.Page<InventoryDtos.CopyDetail> list(Long cardId, Long locationId, int page, int size) {
        if (cardId != null && !cards.existsById(cardId)) throw new DomainException(HttpStatus.NOT_FOUND, "Card not found");
        if (locationId != null && !locations.existsById(locationId)) throw new DomainException(HttpStatus.NOT_FOUND, "Location not found");
        Specification<CardCopy> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("archivedAt")));
            if (cardId != null) predicates.add(cb.equal(root.get("printing").get("card").get("id"), cardId));
            if (locationId != null) predicates.add(cb.equal(root.get("location").get("id"), locationId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = copies.findAll(spec, PageRequest.of(page, size, Sort.by("id")));
        return new InventoryDtos.Page<>(result.getContent().stream().map(InventoryDtos::detail).toList(),
                page, size, result.getTotalElements(), result.hasNext());
    }
    public List<InventoryDtos.Availability> availability(List<Long> cardIds) {
        List<Long> ids = cardIds.stream().distinct().toList();
        if (cards.findAllById(ids).size() != ids.size()) throw new DomainException(HttpStatus.NOT_FOUND, "One or more cards were not found");
        Map<Long,List<InventoryDtos.LocationAvailability>> grouped = new HashMap<>();
        for (var row : copies.summarize(ids)) {
            grouped.computeIfAbsent(row.getCardId(), key -> new ArrayList<>()).add(
                    new InventoryDtos.LocationAvailability(row.getLocationId(), row.getLocationName(), row.getLocationType(),
                            row.getQuantity(), row.getProxyQuantity()));
        }
        List<InventoryDtos.Availability> result = new ArrayList<>();
        for (Long id : ids) {
            var rows = grouped.getOrDefault(id, List.of());
            long owned = rows.stream().mapToLong(InventoryDtos.LocationAvailability::quantity).sum();
            long proxies = rows.stream().mapToLong(InventoryDtos.LocationAvailability::proxyQuantity).sum();
            long inDeck = rows.stream().filter(row -> row.locationType() == LocationType.DECK)
                    .mapToLong(InventoryDtos.LocationAvailability::quantity).sum();
            result.add(new InventoryDtos.Availability(id, owned, owned - proxies, proxies, owned - inDeck, inDeck, List.copyOf(rows)));
        }
        return List.copyOf(result);
    }
}

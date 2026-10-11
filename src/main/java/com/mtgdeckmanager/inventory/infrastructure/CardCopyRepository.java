package com.mtgdeckmanager.inventory.infrastructure;

import com.mtgdeckmanager.inventory.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CardCopyRepository extends JpaRepository<CardCopy, Long>, JpaSpecificationExecutor<CardCopy> {
    @Override
    @EntityGraph(attributePaths = {"printing", "printing.card", "location"})
    Page<CardCopy> findAll(Specification<CardCopy> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"printing", "printing.card", "location"})
    Optional<CardCopy> findById(Long id);

    interface AvailabilityRow {
        Long getCardId(); Long getLocationId(); String getLocationName(); LocationType getLocationType();
        Long getQuantity(); Long getProxyQuantity();
    }
    @Query("""
        select p.card.id as cardId, l.id as locationId, l.name as locationName, l.type as locationType,
               count(c) as quantity, sum(case when c.proxy = true then 1 else 0 end) as proxyQuantity
        from CardCopy c join c.printing p join c.location l
        where c.archivedAt is null and p.card.id in :cardIds
        group by p.card.id, l.id, l.name, l.type
        order by p.card.id, l.id
        """)
    List<AvailabilityRow> summarize(@Param("cardIds") List<Long> cardIds);
}

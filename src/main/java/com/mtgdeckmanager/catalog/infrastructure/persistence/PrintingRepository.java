package com.mtgdeckmanager.catalog.infrastructure.persistence;
import com.mtgdeckmanager.catalog.domain.Printing;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrintingRepository extends JpaRepository<Printing, Long> {
    Optional<Printing> findByScryfallId(String scryfallId);
}

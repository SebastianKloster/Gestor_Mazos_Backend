package com.mtgdeckmanager.catalog.infrastructure.persistence;
import com.mtgdeckmanager.catalog.domain.Card;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByOracleId(String oracleId);
}

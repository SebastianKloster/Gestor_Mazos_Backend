package com.mtgdeckmanager.inventory.infrastructure;
import com.mtgdeckmanager.inventory.domain.CardMovement;
import org.springframework.data.repository.Repository;
public interface CardMovementRepository extends Repository<CardMovement, Long> {
    CardMovement save(CardMovement movement);
}

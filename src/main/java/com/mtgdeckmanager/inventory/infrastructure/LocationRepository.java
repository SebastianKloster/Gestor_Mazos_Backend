package com.mtgdeckmanager.inventory.infrastructure;
import com.mtgdeckmanager.inventory.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LocationRepository extends JpaRepository<Location, Long> { }

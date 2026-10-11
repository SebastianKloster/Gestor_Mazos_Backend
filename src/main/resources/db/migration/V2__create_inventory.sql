CREATE TABLE locations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_locations_type CHECK (type IN ('DECK', 'BINDER', 'BOX', 'OTHER'))
);
CREATE TABLE card_copies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    printing_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    deck_zone VARCHAR(16),
    is_proxy BOOLEAN NOT NULL DEFAULT FALSE,
    is_foil BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_copies_printing FOREIGN KEY (printing_id) REFERENCES printings(id),
    CONSTRAINT fk_copies_location FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT ck_copies_zone CHECK (deck_zone IS NULL OR deck_zone IN ('MAIN', 'COMMANDER')),
    INDEX ix_copies_location_active (location_id, archived_at),
    INDEX ix_copies_printing_active (printing_id, archived_at)
);
CREATE TABLE card_movements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    card_copy_id BIGINT NOT NULL,
    from_location_id BIGINT,
    to_location_id BIGINT,
    from_zone VARCHAR(16),
    to_zone VARCHAR(16),
    movement_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_movements_copy FOREIGN KEY (card_copy_id) REFERENCES card_copies(id),
    CONSTRAINT fk_movements_from FOREIGN KEY (from_location_id) REFERENCES locations(id),
    CONSTRAINT fk_movements_to FOREIGN KEY (to_location_id) REFERENCES locations(id),
    CONSTRAINT ck_movements_type CHECK (movement_type IN ('ACQUIRED', 'TRANSFERRED', 'REMOVED_FROM_COLLECTION')),
    CONSTRAINT ck_movements_from_zone CHECK (from_zone IS NULL OR from_zone IN ('MAIN', 'COMMANDER')),
    CONSTRAINT ck_movements_to_zone CHECK (to_zone IS NULL OR to_zone IN ('MAIN', 'COMMANDER')),
    CONSTRAINT ck_movements_endpoints CHECK (
        (movement_type = 'ACQUIRED' AND from_location_id IS NULL AND from_zone IS NULL AND to_location_id IS NOT NULL)
        OR (movement_type = 'TRANSFERRED' AND from_location_id IS NOT NULL AND to_location_id IS NOT NULL)
        OR (movement_type = 'REMOVED_FROM_COLLECTION' AND from_location_id IS NOT NULL AND to_location_id IS NULL AND to_zone IS NULL)
    ),
    INDEX ix_movements_copy_created (card_copy_id, created_at)
);

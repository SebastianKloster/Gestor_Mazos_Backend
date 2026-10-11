CREATE TABLE cards (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    oracle_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    layout VARCHAR(32) NOT NULL,
    type_line VARCHAR(512),
    mana_value DECIMAL(10,2),
    oracle_text TEXT,
    mana_cost VARCHAR(255),
    color_identity VARCHAR(5) NOT NULL,
    CONSTRAINT uk_cards_oracle_id UNIQUE (oracle_id),
    INDEX ix_cards_name (name)
);
CREATE TABLE card_faces (
    card_id BIGINT NOT NULL,
    face_order INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type_line VARCHAR(512),
    oracle_text TEXT,
    mana_cost VARCHAR(255),
    PRIMARY KEY (card_id, face_order),
    CONSTRAINT fk_card_faces_card FOREIGN KEY (card_id) REFERENCES cards(id)
);
CREATE TABLE printings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    scryfall_id CHAR(36) NOT NULL,
    set_code VARCHAR(16) NOT NULL,
    set_name VARCHAR(255) NOT NULL,
    collector_number VARCHAR(64) NOT NULL,
    image_url VARCHAR(2048),
    back_image_url VARCHAR(2048),
    available_finishes VARCHAR(128) NOT NULL,
    usd_price DECIMAL(12,2),
    usd_foil_price DECIMAL(12,2),
    eur_price DECIMAL(12,2),
    eur_foil_price DECIMAL(12,2),
    price_updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_printings_scryfall_id UNIQUE (scryfall_id),
    CONSTRAINT fk_printings_card FOREIGN KEY (card_id) REFERENCES cards(id),
    INDEX ix_printings_card (card_id)
);

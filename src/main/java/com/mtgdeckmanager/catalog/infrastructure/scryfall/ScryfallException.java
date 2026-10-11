package com.mtgdeckmanager.catalog.infrastructure.scryfall;

public class ScryfallException extends RuntimeException {
    private final boolean rateLimited;
    public ScryfallException(String message, boolean rateLimited) {
        super(message); this.rateLimited = rateLimited;
    }
    public boolean isRateLimited() { return rateLimited; }
}

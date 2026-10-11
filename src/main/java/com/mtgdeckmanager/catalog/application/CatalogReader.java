package com.mtgdeckmanager.catalog.application;

import com.mtgdeckmanager.catalog.api.CatalogDtos;
import com.mtgdeckmanager.catalog.infrastructure.persistence.*;
import com.mtgdeckmanager.common.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogReader {
    private final CardRepository cards;
    private final PrintingRepository printings;
    public CatalogReader(CardRepository cards, PrintingRepository printings) { this.cards = cards; this.printings = printings; }
    public CatalogDtos.CardDetail card(long id) {
        return CatalogDtos.detail(cards.findById(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Card not found")));
    }
    public CatalogDtos.PrintingDetail printing(long id) {
        return CatalogDtos.detail(printings.findById(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Printing not found")));
    }
}

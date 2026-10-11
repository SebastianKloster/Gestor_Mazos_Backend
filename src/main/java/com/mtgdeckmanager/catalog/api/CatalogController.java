package com.mtgdeckmanager.catalog.api;

import com.mtgdeckmanager.catalog.application.*;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Catalog", description = "English-only Scryfall catalog; internal numeric identifiers")
public class CatalogController {
    private final CatalogService service;
    private final CatalogReader reader;
    public CatalogController(CatalogService service, CatalogReader reader) { this.service = service; this.reader = reader; }

    @GetMapping("/cards")
    @Operation(summary = "Search English card names and cache the requested results", description = "Zero-based page; size 1–100. May call Scryfall; not a search restricted to previously imported cards.")
    public CatalogDtos.Page<CatalogDtos.CardSummary> search(@RequestParam @NotBlank @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(search, page, size);
    }
    @GetMapping("/cards/{cardId}")
    @Operation(summary = "Read a previously imported logical card without calling Scryfall")
    public CatalogDtos.CardDetail card(@PathVariable @Positive long cardId) { return reader.card(cardId); }

    @GetMapping("/cards/{cardId}/printings")
    @Operation(summary = "Find English printings of a previously imported logical card")
    public CatalogDtos.Page<CatalogDtos.PrintingDetail> printings(@PathVariable @Positive long cardId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.printings(cardId, page, size);
    }
    @GetMapping("/printings/{printingId}")
    @Operation(summary = "Read an imported printing, images and nullable reference prices")
    public CatalogDtos.PrintingDetail printing(@PathVariable @Positive long printingId) { return reader.printing(printingId); }
}

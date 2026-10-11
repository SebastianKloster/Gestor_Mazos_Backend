package com.mtgdeckmanager.inventory.api;

import com.mtgdeckmanager.inventory.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Individual physical copies; optimistic versions required for changes")
public class InventoryController {
    private final InventoryService service;
    private final InventoryReader reader;
    public InventoryController(InventoryService service, InventoryReader reader) { this.service = service; this.reader = reader; }
    @GetMapping("/copies")
    @Operation(summary = "List active copies, including printing, proxy and foil details")
    public InventoryDtos.Page<InventoryDtos.CopyDetail> list(@RequestParam(required = false) @Positive Long cardId,
            @RequestParam(required = false) @Positive Long locationId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reader.list(cardId, locationId, page, size);
    }
    @PostMapping("/copies") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acquire 1–100 individual copies atomically; never a transfer")
    public List<InventoryDtos.CopyDetail> acquire(@Valid @RequestBody InventoryDtos.CreateCopies request) { return service.acquire(request); }
    @PostMapping("/copies/{id}/move")
    public InventoryDtos.CopyDetail move(@PathVariable @Positive long id, @Valid @RequestBody InventoryDtos.MoveCopy request) { return service.move(id, request); }
    @PatchMapping("/copies/{id}")
    @Operation(summary = "Change proxy/foil flags without changing printing or location")
    public InventoryDtos.CopyDetail update(@PathVariable @Positive long id, @Valid @RequestBody InventoryDtos.PatchCopy request) { return service.updateFlags(id, request); }
    @PostMapping("/copies/{id}/archive")
    @Operation(summary = "Remove a copy from active inventory, preserving its last location and audit")
    public InventoryDtos.CopyDetail archive(@PathVariable @Positive long id, @Valid @RequestBody InventoryDtos.ArchiveCopy request) { return service.archive(id, request); }
    @GetMapping("/availability")
    @Operation(summary = "Batch ownership by logical card and location, counting all proxies and printings",
            description = "Up to 100 IDs. Copy details are fetched via the paginated copies endpoint with cardId/locationId filters.")
    public List<InventoryDtos.Availability> availability(@RequestParam @Size(min = 1, max = 100) List<@Positive Long> cardIds) {
        return reader.availability(cardIds);
    }
}

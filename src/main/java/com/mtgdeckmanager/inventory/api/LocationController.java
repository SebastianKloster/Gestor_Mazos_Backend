package com.mtgdeckmanager.inventory.api;

import com.mtgdeckmanager.inventory.application.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/locations")
@Tag(name = "Locations", description = "BINDER, BOX and OTHER; DECK is managed with a deck in the next stage")
public class LocationController {
    private final LocationService service;
    public LocationController(LocationService service) { this.service = service; }
    @GetMapping
    public InventoryDtos.Page<InventoryDtos.LocationDetail> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.list(page, size); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public InventoryDtos.LocationDetail create(@Valid @RequestBody InventoryDtos.CreateLocation request) { return service.create(request); }
    @PatchMapping("/{id}")
    public InventoryDtos.LocationDetail update(@PathVariable @Positive long id, @Valid @RequestBody InventoryDtos.PatchLocation request) {
        return service.update(id, request);
    }
}

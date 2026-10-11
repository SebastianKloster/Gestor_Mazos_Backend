package com.mtgdeckmanager.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtgdeckmanager.inventory.domain.CardMovement;
import com.mtgdeckmanager.inventory.infrastructure.CardMovementRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIT {
    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.8");
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean CardMovementRepository movements;

    @BeforeEach
    void fixtures() {
        for (String table : List.of("card_movements", "card_copies", "locations", "printings", "card_faces", "cards")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (int i = 1; i <= 2; i++) {
            jdbc.update("""
                INSERT INTO cards(id, oracle_id, name, layout, color_identity)
                VALUES (?, ?, ?, 'normal', '')
                """, 1000 + i, UUID.randomUUID().toString(), "Card " + i);
        }
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                INSERT INTO printings(id, card_id, scryfall_id, set_code, set_name, collector_number, available_finishes, price_updated_at)
                VALUES (?, ?, ?, 'tst', 'Test Set', ?, 'nonfoil,foil', CURRENT_TIMESTAMP(6))
                """, 2000 + i, i <= 2 ? 1001 : 1002, UUID.randomUUID().toString(), String.valueOf(i));
        }
    }

    @Test
    void managesNonDeckLocationsAndRejectsOrphansOrBlankNames() throws Exception {
        JsonNode location = location("Binder", "BINDER");long id = location.get("id").asLong();
        JsonNode updated = ok(HttpMethod.PATCH, "/api/locations/" + id, Map.of("name", "New name", "type", "BOX"), 200);
        assertThat(updated.get("name").asText()).isEqualTo("New name");
        assertThat(updated.get("type").asText()).isEqualTo("BOX");
        assertThat(get("/api/locations?size=1").get("content").size()).isEqualTo(1);
        assertThat(send(HttpMethod.POST, "/api/locations", Map.of("name", "Deck", "type", "DECK")).getStatusCode().value()).isEqualTo(422);
        assertThat(send(HttpMethod.PATCH, "/api/locations/" + id, Map.of("type", "DECK")).getStatusCode().value()).isEqualTo(422);
        assertThat(send(HttpMethod.PATCH, "/api/locations/" + id, Map.of("name", "   ")).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/locations", Map.of("name", " ", "type", "BOX")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void acquiresIndividualCopiesAndAggregatesPrintingsAndProxiesByLogicalCard() throws Exception {
        long binder = location("Binder", "BINDER").get("id").asLong();
        JsonNode copies = acquire(2001, binder, 3, true, true);
        acquire(2002, binder, 1, false, false);
        assertThat(copies.size()).isEqualTo(3);
        assertThat(copies.get(0).get("id").asLong()).isNotEqualTo(copies.get(1).get("id").asLong());
        assertThat(copies.get(0).get("isProxy").asBoolean()).isTrue();
        assertThat(copies.get(0).get("isFoil").asBoolean()).isTrue();
        JsonNode page = get("/api/inventory/copies?cardId=1001&locationId=" + binder + "&size=2");
        assertThat(page.get("content").size()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        JsonNode availability = get("/api/inventory/availability?cardIds=1001,1002");
        assertThat(availability.get(0).get("ownedQuantity").asInt()).isEqualTo(4);
        assertThat(availability.get(0).get("proxyQuantity").asInt()).isEqualTo(3);
        assertThat(availability.get(0).get("authenticQuantity").asInt()).isEqualTo(1);
        assertThat(availability.get(0).get("freeQuantity").asInt()).isEqualTo(4);
        assertThat(availability.get(1).get("ownedQuantity").asInt()).isZero();
        assertThat(count("SELECT COUNT(*) FROM card_movements WHERE movement_type = 'ACQUIRED'")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM card_movements WHERE from_location_id IS NOT NULL")).isZero();
        assertThat(get("/api/inventory/availability?cardIds=1001,1001").size()).isEqualTo(1);
    }

    @Test
    void transferChangesOneLocationAndRecordsOriginAndDestination() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long destination = location("Box", "BOX").get("id").asLong();
        JsonNode copy = acquire(2001, origin, 1, true, false).get(0);long id = copy.get("id").asLong();
        JsonNode moved = ok(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", destination, "expectedVersion", 0), 200);
        assertThat(moved.get("locationId").asLong()).isEqualTo(destination);
        assertThat(moved.get("version").asLong()).isEqualTo(1);
        assertThat(moved.get("isProxy").asBoolean()).isTrue();
        assertThat(get("/api/inventory/copies?locationId=" + origin).get("totalElements").asInt()).isZero();
        assertThat(get("/api/inventory/copies?locationId=" + destination).get("totalElements").asInt()).isEqualTo(1);
        var audit = jdbc.queryForMap("SELECT from_location_id, to_location_id FROM card_movements WHERE movement_type = 'TRANSFERRED'");
        assertThat(((Number)audit.get("from_location_id")).longValue()).isEqualTo(origin);
        assertThat(((Number)audit.get("to_location_id")).longValue()).isEqualTo(destination);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", origin, "expectedVersion", 0)).getStatusCode().value()).isEqualTo(409);
        assertThat(count("SELECT COUNT(*) FROM card_movements")).isEqualTo(2);
        assertThat(get("/api/inventory/availability?cardIds=1001").get(0).get("ownedQuantity").asInt()).isEqualTo(1);
    }

    @Test
    void invalidZoneAndNoOpDoNotChangeInventoryOrAudit() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long destination = location("Box", "BOX").get("id").asLong();
        long id = acquire(2001, origin, 1, false, false).get(0).get("id").asLong();
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", destination, "toZone", "MAIN", "expectedVersion", 0)).getStatusCode().value()).isEqualTo(422);
        JsonNode noOp = ok(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", origin, "expectedVersion", 0), 200);
        assertThat(noOp.get("version").asInt()).isZero();
        assertThat(count("SELECT COUNT(*) FROM card_movements")).isEqualTo(1);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies",
                Map.of("printingId", 2001, "locationId", destination, "quantity", 1, "deckZone", "COMMANDER")).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void archivePreservesPhysicalRecordAndAuditButExcludesAllActiveQueries() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long id = acquire(2001, origin, 1, true, true).get(0).get("id").asLong();
        JsonNode archived = ok(HttpMethod.POST, "/api/inventory/copies/" + id + "/archive", Map.of("expectedVersion", 0), 200);
        assertThat(archived.get("archivedAt").isNull()).isFalse();
        assertThat(archived.get("locationId").asLong()).isEqualTo(origin);
        assertThat(get("/api/inventory/copies").get("totalElements").asInt()).isZero();
        assertThat(get("/api/inventory/availability?cardIds=1001").get(0).get("ownedQuantity").asInt()).isZero();
        assertThat(count("SELECT COUNT(*) FROM card_copies")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM card_movements WHERE movement_type='REMOVED_FROM_COLLECTION' AND to_location_id IS NULL")).isEqualTo(1);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/archive", Map.of("expectedVersion", 1)).getStatusCode().value()).isEqualTo(409);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", origin, "expectedVersion", 1)).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void flagsAreEditableWithVersionWithoutMovingOrDuplicatingCopies() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long id = acquire(2001, origin, 1, true, false).get(0).get("id").asLong();
        JsonNode changed = ok(HttpMethod.PATCH, "/api/inventory/copies/" + id,
                Map.of("expectedVersion", 0, "isProxy", false, "isFoil", true), 200);
        assertThat(changed.get("isProxy").asBoolean()).isFalse();
        assertThat(changed.get("isFoil").asBoolean()).isTrue();
        assertThat(changed.get("printingId").asInt()).isEqualTo(2001);
        assertThat(changed.get("locationId").asLong()).isEqualTo(origin);
        assertThat(changed.get("version").asInt()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM card_movements")).isEqualTo(1);
        assertThat(get("/api/inventory/availability?cardIds=1001").get(0).get("proxyQuantity").asInt()).isZero();
        assertThat(send(HttpMethod.PATCH, "/api/inventory/copies/" + id,
                Map.of("expectedVersion", 0, "isProxy", true)).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void rejectsMissingVersionsInvalidQuantitiesAndUnknownIdsWithoutWrites() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long id = acquire(2001, origin, 1, false, false).get(0).get("id").asLong();
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move", Map.of("toLocationId", origin)).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies",
                Map.of("printingId", 2001, "locationId", origin, "quantity", 101)).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies",
                Map.of("printingId", 999999, "locationId", origin, "quantity", 1)).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", 999999, "expectedVersion", 0)).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/inventory/availability?cardIds=-1", String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/api/inventory/availability?cardIds=999999", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/inventory/copies?size=101", String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(count("SELECT COUNT(*) FROM card_copies")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM card_movements")).isEqualTo(1);
    }

    @Test
    void twoConcurrentTransfersHaveExactlyOneWinnerAndOneAuditEvent() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long first = location("First", "BOX").get("id").asLong();
        long second = location("Second", "OTHER").get("id").asLong();
        long id = acquire(2001, origin, 1, false, false).get(0).get("id").asLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Future<Integer> a = executor.submit(() -> { gate.await();return send(HttpMethod.POST,
                    "/api/inventory/copies/" + id + "/move", Map.of("toLocationId", first, "expectedVersion", 0)).getStatusCode().value(); });
            Future<Integer> b = executor.submit(() -> { gate.await();return send(HttpMethod.POST,
                    "/api/inventory/copies/" + id + "/move", Map.of("toLocationId", second, "expectedVersion", 0)).getStatusCode().value(); });
            gate.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(count("SELECT COUNT(*) FROM card_movements WHERE movement_type='TRANSFERRED'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM card_copies WHERE id=?", Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT location_id FROM card_copies WHERE id=?", Long.class,id)).isIn(first,second);
    }

    @Test
    void failureToRecordAuditRollsBackAnAlreadyFlushedTransfer() throws Exception {
        long origin = location("Binder", "BINDER").get("id").asLong();
        long destination = location("Box", "BOX").get("id").asLong();
        long id = acquire(2001, origin, 1, false, false).get(0).get("id").asLong();
        doThrow(new IllegalStateException("Simulated audit failure")).when(movements).save(any(CardMovement.class));
        assertThat(send(HttpMethod.POST, "/api/inventory/copies/" + id + "/move",
                Map.of("toLocationId", destination, "expectedVersion", 0)).getStatusCode().value()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT location_id FROM card_copies WHERE id=?", Long.class,id)).isEqualTo(origin);
        assertThat(jdbc.queryForObject("SELECT version FROM card_copies WHERE id=?", Long.class,id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM card_movements")).isEqualTo(1);
    }

    private int count(String sql) { return jdbc.queryForObject(sql,Integer.class); }
    private JsonNode location(String name,String type) throws Exception {
        return ok(HttpMethod.POST,"/api/locations",Map.of("name",name,"type",type),201);
    }
    private JsonNode acquire(long printing,long location,int quantity,boolean proxy,boolean foil) throws Exception {
        return ok(HttpMethod.POST,"/api/inventory/copies", Map.of("printingId",printing,"locationId",location,
                "quantity",quantity,"isProxy",proxy,"isFoil",foil),201);
    }
    private ResponseEntity<String> send(HttpMethod method,String path,Object body) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
    }
    private JsonNode ok(HttpMethod method,String path,Object body,int expected) throws Exception {
        var response=send(method,path,body);
        assertThat(response.getStatusCode().value()).withFailMessage(response.getBody()).isEqualTo(expected);
        return json.readTree(response.getBody());
    }
    private JsonNode get(String path) throws Exception { return ok(HttpMethod.GET,path,null,200); }
}

package com.mtgdeckmanager.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIT {
    @Container @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.8");
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> queries = new CopyOnWriteArrayList<>();
    static final List<String> agents = new CopyOnWriteArrayList<>();
    static final List<String> accepts = new CopyOnWriteArrayList<>();
    static final HttpServer upstream = startServer();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("scryfall.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }
    @org.junit.jupiter.api.AfterAll static void stopServer() { upstream.stop(0); }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importsEnglishCardAndPrintingsByOracleIdentityAndCachesSearches() throws Exception {
        JsonNode first = get("/api/cards?search=Reanimate");
        long id = first.at("/content/0/id").asLong();
        int calls = queries.size();
        JsonNode again = get("/api/cards?search=Reanimate");
        assertThat(again.at("/content/0/id").asLong()).isEqualTo(id);
        assertThat(queries.size()).isEqualTo(calls);
        JsonNode card = get("/api/cards/" + id);
        assertThat(card.get("name").asText()).isEqualTo("Reanimate");
        assertThat(queries.size()).isEqualTo(calls);
        JsonNode prints = get("/api/cards/" + id + "/printings");
        assertThat(prints.get("totalElements").asInt()).isEqualTo(2);
        assertThat(prints.get("content").size()).isEqualTo(2);
        assertThat(prints.at("/content/0/cardId").asLong()).isEqualTo(id);
        assertThat(prints.at("/content/1/cardId").asLong()).isEqualTo(id);
        assertThat(prints.at("/content/0/id").asLong()).isNotEqualTo(prints.at("/content/1/id").asLong());
        long printingId = prints.at("/content/0/id").asLong();
        JsonNode printing = get("/api/printings/" + printingId);
        assertThat(printing.get("usdPrice").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(printing.get("usdFoilPrice").isNull()).isTrue();
        assertThat(printing.get("eurPrice").decimalValue()).isEqualByComparingTo("0.00");
        String timestamp = printing.get("priceUpdatedAt").asText();
        get("/api/cards?search=Reanimate");
        assertThat(get("/api/printings/" + printingId).get("priceUpdatedAt").asText()).isEqualTo(timestamp);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE oracle_id = ?", Integer.class,
                uuid("Reanimate"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM printings WHERE card_id = ?", Integer.class,id)).isEqualTo(2);
        assertThat(queries).anyMatch(q -> q.contains("name:\"Reanimate\" lang:en"));
        assertThat(agents).allMatch(a -> a.equals("MTGDeckManager/0.0.1"));
        assertThat(accepts).allMatch(a -> a.equals("application/json"));
    }

    @Test
    void mapsBothFacesAndTheirNormalImages() throws Exception {
        long id = get("/api/cards?search=Delver").at("/content/0/id").asLong();
        JsonNode card = get("/api/cards/" + id);
        assertThat(card.get("faces").size()).isEqualTo(2);
        assertThat(card.at("/faces/1/name").asText()).isEqualTo("Insectile Aberration");
        JsonNode printing = get("/api/cards/" + id + "/printings").at("/content/0");
        assertThat(printing.get("imageUrl").asText()).isEqualTo("https://cards.scryfall.io/front.jpg");
        assertThat(printing.get("backImageUrl").asText()).isEqualTo("https://cards.scryfall.io/back.jpg");
    }

    @Test
    void combinedImageIsNotMistakenForTwoSeparateFaceImages() throws Exception {
        long id = get("/api/cards?search=Adventure").at("/content/0/id").asLong();
        JsonNode printing = get("/api/cards/" + id + "/printings").at("/content/0");
        assertThat(printing.get("imageUrl").asText()).isEqualTo("https://cards.scryfall.io/combined.jpg");
        assertThat(printing.get("backImageUrl").isNull()).isTrue();
    }

    @Test
    void paginatesAcrossScryfallPageBoundaryAndPreservesTotalForOutOfRange() throws Exception {
        JsonNode page = get("/api/cards?search=Paged&page=8&size=20");
        assertThat(page.get("content").size()).isEqualTo(20);
        assertThat(page.at("/content/0/name").asText()).isEqualTo("Paged 160");
        assertThat(page.at("/content/19/name").asText()).isEqualTo("Paged 179");
        assertThat(page.get("totalElements").asInt()).isEqualTo(180);
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        JsonNode empty = get("/api/cards?search=Paged&page=10&size=20");
        assertThat(empty.get("content").size()).isZero();
        assertThat(empty.get("totalElements").asInt()).isEqualTo(180);
    }

    @Test
    void emptySearchHasAnEmptyPage() throws Exception {
        JsonNode result = get("/api/cards?search=Missing");
        assertThat(result.get("content").size()).isZero();
        assertThat(result.get("totalElements").asInt()).isZero();
    }

    @Test
    void nonEnglishResponseRollsBackEntireImport() {
        int before = countCards();
        var response = http.getForEntity("/api/cards?search=Foreign", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(countCards()).isEqualTo(before);
    }

    @Test
    void missingOracleIdentityIsRejectedWithoutInventingAnId() {
        int before = countCards();
        var response = http.getForEntity("/api/cards?search=Unsupported", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(countCards()).isEqualTo(before);
    }

    @Test
    void upstreamFailuresBecomeProblemDetailsWithoutInternalData() {
        var response = http.getForEntity("/api/cards?search=Failure", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).doesNotContain("provider internal error");
    }

    @Test
    void rejectsInvalidPaginationAndUnknownInternalIdentifiers() {
        for (String path : List.of("/api/cards?search=", "/api/cards?search=x&page=-1",
                "/api/cards?search=x&size=101", "/api/cards?search=x&size=0", "/api/cards/-1")) {
            assertThat(http.getForEntity(path, String.class).getStatusCode().value()).isEqualTo(400);
        }
        int calls = queries.size();
        assertThat(http.getForEntity("/api/cards/999999999", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/printings/999999999", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/cards/999999999/printings", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(queries.size()).isEqualTo(calls);
    }

    private int countCards() { return jdbc.queryForObject("SELECT COUNT(*) FROM cards", Integer.class); }
    private JsonNode get(String path) throws Exception {
        var response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode().value()).withFailMessage(response.getBody()).isEqualTo(200);
        return JSON.readTree(response.getBody());
    }
    static String uuid(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString(); }
    static Map<String,Object> card(String name, String printingKey) {
        Map<String,Object> d = new HashMap<>();
        d.put("id", uuid(printingKey)); d.put("oracle_id", uuid(name)); d.put("name", name);
        d.put("lang", "en"); d.put("layout", "normal"); d.put("type_line", "Sorcery");
        d.put("cmc", 1); d.put("oracle_text", "English Oracle text"); d.put("mana_cost", "{B}");
        d.put("color_identity", List.of("B")); d.put("set", "tst"); d.put("set_name", "Test Set");
        d.put("collector_number", "123a"); d.put("finishes", List.of("nonfoil", "foil"));
        d.put("image_uris", Map.of("normal", "https://cards.scryfall.io/combined.jpg"));
        Map<String,String> prices = new HashMap<>();prices.put("usd", "12.34"); prices.put("eur", "0.00");prices.put("usd_foil", null);
        d.put("prices", prices);return d;
    }
    static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/cards/search", exchange -> {
                Map<String,String> params = new HashMap<>();
                for (String part : exchange.getRequestURI().getRawQuery().split("&")) {
                    String[] pair = part.split("=",2);
                    params.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
                String q = params.get("q");queries.add(q);
                agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
                accepts.add(exchange.getRequestHeaders().getFirst("Accept"));
                int status = 200;List<Map<String,Object>> data = new ArrayList<>();
                String name = "Reanimate";
                if (q.contains("Missing")) status = 404;
                else if (q.contains("Failure")) status = 500;
                else if (q.contains("Paged")) {
                    for (int i=0;i<180;i++) data.add(card("Paged " + i,"Paged printing " + i));
                } else if (q.contains("Foreign")) {
                    data.add(card("Foreign English", "Foreign English"));
                    var foreign=card("Foreign Spanish", "Foreign Spanish");foreign.put("lang","es");data.add(foreign);
                } else if (q.contains("Unsupported")) {
                    var unsupported=card("Unsupported", "Unsupported");unsupported.remove("oracle_id");data.add(unsupported);
                } else {
                    if (q.contains("Delver") || q.contains(uuid("Delver"))) name="Delver";
                    if (q.contains("Adventure") || q.contains(uuid("Adventure"))) name="Adventure";
                    var one=card(name,name+" first");
                    if (!name.equals("Reanimate")) {
                        one.put("layout",name.equals("Delver")?"transform":"adventure");
                        one.put("card_faces",List.of(
                            Map.of("name",name,"type_line","Creature", "mana_cost","{U}","oracle_text","Front rules",
                                   "image_uris",Map.of("normal","https://cards.scryfall.io/front.jpg")),
                            Map.of("name","Insectile Aberration","type_line","Creature", "mana_cost","", "oracle_text","Back rules",
                                   "image_uris",Map.of("normal","https://cards.scryfall.io/back.jpg"))));
                        if (name.equals("Delver")) one.remove("image_uris");
                    }
                    data.add(one);
                    if (params.get("unique").equals("prints") && name.equals("Reanimate"))
                        data.add(card(name,name+" second"));
                }
                int total=data.size();int page=Integer.parseInt(params.get("page"));int from=Math.min(total,(page-1)*175);
                var result=Map.of("object","list","data",data.subList(from,Math.min(total,from+175)),
                        "total_cards",total,"has_more",from+175<total);
                byte[] bytes=JSON.writeValueAsBytes(status==200 ? result : Map.of("details","provider internal error"));
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });
            server.start();return server;
        } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
}

package com.mtgdeckmanager.catalog.infrastructure.scryfall;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.mtgdeckmanager.catalog.infrastructure.scryfall.ScryfallDtos.SearchResult;

@Component
public class ScryfallClient {
    public static final int PAGE_SIZE = 175;
    private final RestClient http;
    private long nextRequestNanos;
    private long backoffUntilNanos;
    public ScryfallClient(RestClient scryfallRestClient) { this.http = scryfallRestClient; }

    // Serial requests at most 8/second; no retries and a cooldown after HTTP 429.
    public synchronized SearchResult search(String query, String unique, int page) {
        long now = System.nanoTime();
        if (now < backoffUntilNanos) throw new ScryfallException("Scryfall is rate limited; retry later", true);
        while ((now = System.nanoTime()) < nextRequestNanos) {
            LockSupport.parkNanos(nextRequestNanos - now);
            if (Thread.currentThread().isInterrupted()) throw new ScryfallException("Scryfall request interrupted", false);
        }
        nextRequestNanos = System.nanoTime() + Duration.ofMillis(125).toNanos();
        try {
            SearchResult result = http.get().uri(b -> b.path("/cards/search")
                    .queryParam("q", "{query}").queryParam("unique", unique)
                    .queryParam("order", "name").queryParam("page", page).build(query))
                    .retrieve().body(SearchResult.class);
            if (result == null || result.data() == null || result.totalCards() < 0
                    || result.data().size() > PAGE_SIZE) {
                throw new ScryfallException("Invalid response from Scryfall", false);
            }
            return result;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return new SearchResult(List.of(), 0, false);
            if (ex.getStatusCode().value() == 429) {
                long seconds = 60;
                try {
                    String value = ex.getResponseHeaders().getFirst("Retry-After");
                    if (value != null) seconds = Math.max(1, Math.min(3600, Long.parseLong(value)));
                } catch (NumberFormatException ignored) { }
                backoffUntilNanos = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
                throw new ScryfallException("Scryfall is rate limited; retry later", true);
            }
            throw new ScryfallException("Scryfall request failed", false);
        } catch (RestClientException ex) {
            throw new ScryfallException("Scryfall is unavailable or returned invalid data", false);
        }
    }
}

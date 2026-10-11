package com.mtgdeckmanager.catalog.application;

import com.mtgdeckmanager.catalog.api.CatalogDtos;
import com.mtgdeckmanager.catalog.api.CatalogDtos.Page;
import com.mtgdeckmanager.catalog.infrastructure.scryfall.*;
import com.mtgdeckmanager.catalog.infrastructure.scryfall.ScryfallDtos.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final ScryfallClient scryfall;
    private final CatalogImporter importer;
    private final CatalogReader reader;
    private final Duration ttl;
    private record Key(String query, String unique, int page) { }
    private record Cached(SearchResult result, Instant fetchedAt, Instant expiresAt) { }
    private record Selection(List<CardData> data, long totalCards, Instant fetchedAt) { }
    private final Map<Key, Cached> cache = new LinkedHashMap<>();
    public CatalogService(ScryfallClient scryfall, CatalogImporter importer, CatalogReader reader,
            @Value("${scryfall.cache-ttl:PT15M}") Duration ttl) {
        this.scryfall = scryfall; this.importer = importer; this.reader = reader; this.ttl = ttl;
    }
    public synchronized Page<CatalogDtos.CardSummary> search(String name, int page, int size) {
        String escaped = name.strip().replace("\\", "\\\\").replace("\"", "\\\"");
        var slice = slice("name:\"" + escaped + "\" lang:en", "cards", page, size);
        var rows = importer.importCards(slice.data(), null, slice.fetchedAt());
        return new Page<>(rows.stream().map(CatalogImporter.Imported::card).toList(), page, size,
                slice.totalCards(), (long) (page + 1) * size < slice.totalCards());
    }
    public synchronized Page<CatalogDtos.PrintingDetail> printings(long cardId, int page, int size) {
        String oracle = reader.card(cardId).oracleId();
        var slice = slice("oracleid:" + oracle + " lang:en", "prints", page, size);
        var rows = importer.importCards(slice.data(), oracle, slice.fetchedAt());
        return new Page<>(rows.stream().map(CatalogImporter.Imported::printing).toList(), page, size,
                slice.totalCards(), (long) (page + 1) * size < slice.totalCards());
    }
    private Selection slice(String query, String unique, int page, int size) {
        long offset = (long) page * size;
        Cached beginning = fetch(query, unique, 1);
        long total = beginning.result().totalCards();
        if (offset >= total) return new Selection(List.of(), total, beginning.fetchedAt());
        int remotePage = (int) (offset / ScryfallClient.PAGE_SIZE) + 1;
        Cached cached = remotePage == 1 ? beginning : fetch(query, unique, remotePage);
        SearchResult first = cached.result();
        Instant fetchedAt = cached.fetchedAt();
        int localOffset = (int) (offset % ScryfallClient.PAGE_SIZE);
        if (localOffset >= first.data().size()) throw new ScryfallException("Inconsistent Scryfall pagination", false);
        List<CardData> selected = new ArrayList<>(first.data().subList(localOffset,
                Math.min(first.data().size(), localOffset + size)));
        if (selected.size() < size && first.hasMore()) {
            Cached next = fetch(query, unique, remotePage + 1);
            selected.addAll(next.result().data().subList(0, Math.min(next.result().data().size(), size - selected.size())));
            if (next.fetchedAt().isBefore(fetchedAt)) fetchedAt = next.fetchedAt();
        }
        return new Selection(List.copyOf(selected), total, fetchedAt);
    }
    private Cached fetch(String query, String unique, int page) {
        Key key = new Key(query, unique, page);
        Cached hit = cache.get(key);
        if (hit != null && Instant.now().isBefore(hit.expiresAt())) return hit;
        SearchResult result = scryfall.search(query, unique, page);
        Instant fetchedAt = Instant.now();
        Cached cached = new Cached(result, fetchedAt, fetchedAt.plus(ttl));
        cache.put(key, cached);
        if (cache.size() > 256) cache.remove(cache.keySet().iterator().next());
        return cached;
    }
}

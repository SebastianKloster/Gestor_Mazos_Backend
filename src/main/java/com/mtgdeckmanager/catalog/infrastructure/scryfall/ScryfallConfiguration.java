package com.mtgdeckmanager.catalog.infrastructure.scryfall;

import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ScryfallConfiguration {
    @Bean
    RestClient scryfallRestClient(@Value("${scryfall.base-url:https://api.scryfall.com}") String baseUrl,
            @Value("${scryfall.user-agent:MTGDeckManager/0.0.1}") String userAgent) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy);
            var address = new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort());
            builder.proxy(new ProxySelector() {
                public List<Proxy> select(URI destination) {
                    return List.of(destination.getHost().equals("localhost") || destination.getHost().equals("127.0.0.1")
                            ? Proxy.NO_PROXY : new Proxy(Proxy.Type.HTTP, address));
                }
                public void connectFailed(URI uri, SocketAddress sa, java.io.IOException e) { }
            });
        }
        var factory = new JdkClientHttpRequestFactory(builder.build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", userAgent).defaultHeader("Accept", "application/json").build();
    }
}

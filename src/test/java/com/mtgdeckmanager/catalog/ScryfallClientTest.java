package com.mtgdeckmanager.catalog;

import com.mtgdeckmanager.catalog.infrastructure.scryfall.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ScryfallClientTest {
    @Test
    void rateLimitDoesNotRetryAndStopsSubsequentCallsDuringCooldown() {
        var builder=RestClient.builder().baseUrl("https://api.scryfall.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","60"));
        var client=new ScryfallClient(builder.build());
        assertThatThrownBy(() -> client.search("lang:en","cards",1))
                .isInstanceOfSatisfying(ScryfallException.class,e -> org.assertj.core.api.Assertions.assertThat(e.isRateLimited()).isTrue());
        assertThatThrownBy(() -> client.search("lang:en","cards",1)).isInstanceOf(ScryfallException.class);
        server.verify();
    }
    @Test
    void malformedResponseIsReportedAsUpstreamFailure() {
        var builder=RestClient.builder().baseUrl("https://api.scryfall.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{}",org.springframework.http.MediaType.APPLICATION_JSON));
        var client=new ScryfallClient(builder.build());
        assertThatThrownBy(() -> client.search("lang:en","cards",1)).isInstanceOf(ScryfallException.class);
        server.verify();
    }
}

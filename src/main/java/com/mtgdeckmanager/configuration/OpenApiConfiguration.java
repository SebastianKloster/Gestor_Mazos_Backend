package com.mtgdeckmanager.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI mtgDeckManagerOpenApi() {
        return new OpenAPI().info(new Info().title("MTG Deck Manager API")
                .version("v1").description("Backend monousuario. Etapa 3: catálogo e inventario de copias físicas."));
    }
}

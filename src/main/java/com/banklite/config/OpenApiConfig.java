package com.banklite.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankLiteOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BankLite API")
                .description("Transactional banking REST service with optimistic locking and idempotent transfers")
                .version("0.1.0"));
    }
}

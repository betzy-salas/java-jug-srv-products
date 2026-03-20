package com.sdd.products.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * Swagger UI available at /swagger-ui.html
 * OpenAPI spec available at /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SDD Products API")
                        .version("1.0.0")
                        .description("REST service for e-commerce product catalog management. " +
                                "Provides CRUD operations with strict business rule validation, " +
                                "soft-delete support for auditing, and inventory control."));
    }
}

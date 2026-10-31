package com.tickethub.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketHub Notification Service API")
                        .version("v1")
                        .description("Kafka-driven e-mail notifications. History, retry and a demo trigger."));
    }
}

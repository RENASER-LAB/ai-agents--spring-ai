package com.renaser.ai.ai_engine.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI renaserOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RENASER OS - AI Engine")
                        .description("API de orquestación de agentes IA (RENASER_AGENT_CONSTITUTION_V2)")
                        .version("v2"));
    }
}

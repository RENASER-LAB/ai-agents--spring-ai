package com.renaser.ai.ai_engine.ai.config;

import com.renaser.ai.ai_engine.ai.supabase.SupabaseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class SupabaseConfig {

    private final SupabaseProperties supabaseProperties;

    @Bean
    public RestClient supabaseRestClient() {
        return RestClient.builder()
                .baseUrl(supabaseProperties.getUrl() + "/rest/v1")
                .defaultHeader("apikey", supabaseProperties.getServiceRoleKey())
                .defaultHeader("Authorization", "Bearer " + supabaseProperties.getServiceRoleKey())
                .requestFactory(timeoutAwareRequestFactory())
                .build();
    }

    // Sin timeout, una caída de Supabase deja el hilo esperando indefinidamente y arrastra
    // consigo la corrida del agente. Preferimos fallar rápido: el resolver degrada a
    // "sin contexto externo" y el agente responde igual, declarando el dato faltante.
    private SimpleClientHttpRequestFactory timeoutAwareRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}

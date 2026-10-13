package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Qué bot hay detrás, según {@code app.whatsapp.modo}.
 *
 * <p>Un modo desconocido cuenta como apagado, y lo dice: un valor mal escrito no puede encender
 * nada. El modo real sin dirección HTTPS o sin secreto no arranca, igual que el backend sin la
 * clave de los tokens: arrancar a medias sería mandar avisos sin firma o a ninguna parte.
 */
@Configuration
@Slf4j
public class ConfiguracionClienteConversacion {

    @Bean
    ClienteConversacion clienteConversacion(PropiedadesWhatsapp propiedades) {
        String modo = propiedades.getModo() == null ? ""
                : propiedades.getModo().trim().toLowerCase(Locale.ROOT);
        switch (modo) {
            case PropiedadesWhatsapp.REAL -> {
                ClienteConversacionHttp.exigirConfiguracion(propiedades);
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, propiedades.getSegundosDeEspera())))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
                log.info("Bot de WhatsApp: modo real, contra {}", propiedades.getUrlServicio());
                return new ClienteConversacionHttp(propiedades, http, Clock.systemUTC());
            }
            case PropiedadesWhatsapp.FALSO -> {
                log.info("Bot de WhatsApp: modo falso (en memoria, no sale nada)");
                return new ClienteConversacionFalso();
            }
            case PropiedadesWhatsapp.APAGADO -> {
                return new ClienteConversacionApagado();
            }
            default -> {
                log.warn("app.whatsapp.modo={} no existe (apagado, falso o real): queda apagado",
                        propiedades.getModo());
                return new ClienteConversacionApagado();
            }
        }
    }
}

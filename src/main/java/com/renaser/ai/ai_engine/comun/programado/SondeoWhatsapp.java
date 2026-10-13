package com.renaser.ai.ai_engine.comun.programado;

import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lo que EX le debe al bot de WhatsApp (V73): los avisos «te toca», los anuncios que no llegaron
 * al primer intento y las revocaciones. Ver {@link EnvioAlBot}.
 *
 * <p>Corre cada pocos segundos y aparte de {@link SondeoVencimientos}: un aviso de «tu prueba ya
 * está disponible» no puede esperar al minuto, y una llamada al bot que tarda no puede retrasar
 * el cierre de lo que venció. Con el modo apagado no hace nada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SondeoWhatsapp {

    private final EnvioAlBot envio;

    @Scheduled(fixedDelayString = "${app.whatsapp.sondeo-ms:10000}",
            initialDelayString = "${app.whatsapp.sondeo-ms:10000}")
    public void ejecutar() {
        try {
            envio.enviarPendientes();
        } catch (Exception e) {
            log.error("El sondeo de WhatsApp falló, se reintenta en el próximo ciclo", e);
        }
    }
}

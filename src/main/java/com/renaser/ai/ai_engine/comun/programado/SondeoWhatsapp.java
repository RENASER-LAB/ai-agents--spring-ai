package com.renaser.ai.ai_engine.comun.programado;

import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioDeEventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lo que EX le debe al bot de WhatsApp (V73): los avisos «te toca», los anuncios que no llegaron
 * al primer intento y las revocaciones ({@link EnvioAlBot}), y desde la V76 los eventos v2 de
 * resultado y de convocatoria cerrada ({@link EnvioDeEventos}).
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
    private final EnvioDeEventos eventos;

    @Scheduled(fixedDelayString = "${app.whatsapp.sondeo-ms:10000}",
            initialDelayString = "${app.whatsapp.sondeo-ms:10000}")
    public void ejecutar() {
        try {
            envio.enviarPendientes();
        } catch (Exception e) {
            log.error("El sondeo de WhatsApp falló, se reintenta en el próximo ciclo", e);
        }
        // Aparte: un fallo de los avisos no frena los eventos, ni al revés.
        try {
            eventos.enviarPendientes();
        } catch (Exception e) {
            log.error("El sondeo de los eventos de WhatsApp falló, se reintenta en el próximo ciclo", e);
        }
    }
}

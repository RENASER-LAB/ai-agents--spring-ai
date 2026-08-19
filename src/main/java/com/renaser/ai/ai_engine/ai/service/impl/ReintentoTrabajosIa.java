package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El vigilante de la cola de calificación.
 *
 * <p>«Se reintenta automáticamente» no puede depender solo de RabbitMQ: si el mensaje se
 * pierde, o si el proceso se cae con el trabajo a medias, no hay nadie que vuelva a intentar
 * y la postulación se queda esperando para siempre. Este sondeo es esa red.
 *
 * <p>Corre cada cinco minutos y solo mira columnas indexadas por estado y fecha, así que no
 * cuesta nada. Vive junto a la cola y no en el sondeo de vencimientos del módulo de
 * selección para no meter dos módulos en el mismo archivo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReintentoTrabajosIa {

    private final ColaCalificacionIa cola;

    @Scheduled(fixedDelayString = "${renaser.ai.calificacion.periodo-sondeo-ms:300000}")
    public void ejecutar() {
        try {
            cola.reintentarAtascados();
        } catch (Exception e) {
            log.error("El sondeo de trabajos de IA falló, se reintenta en el próximo ciclo", e);
        }
    }
}

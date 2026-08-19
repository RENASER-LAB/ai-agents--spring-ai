package com.renaser.ai.ai_engine.comun.programado;

import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lo que "no existe entregar tarde" exige que alguien vigile.
 *
 * <p>Ni la evaluación del hito 2 ni la prueba del hito 3 se cierran solas cuando se acaba
 * el plazo: alguien tiene que darse cuenta. Sin este sondeo, una postulación abandonada se
 * queda esperando para siempre, y eso es exactamente el mismo callejón sin salida que se
 * corrigió para el portal — solo que del lado de "el tiempo pasó", no de "falta un endpoint".
 *
 * <p>El periodo por defecto (1 minuto) es corto a propósito: nada caro corre aquí — dos
 * consultas sobre columnas indexadas por fecha — y la alternativa (dejarlo en horas) deja
 * al candidato viendo un reloj en negativo sin que el sistema reaccione.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SondeoVencimientos {

    private final ServicioEvaluacion evaluacion;
    private final ServicioPrueba prueba;
    private final ServicioValidacion validacion;

    @Scheduled(fixedDelayString = "${app.sondeo.periodo-ms:60000}")
    public void ejecutar() {
        try {
            evaluacion.cerrarVencidas();
        } catch (Exception e) {
            log.error("El sondeo de evaluaciones vencidas falló, se reintenta en el próximo ciclo", e);
        }
        try {
            prueba.entregarVencidos();
        } catch (Exception e) {
            log.error("El sondeo de pruebas vencidas falló, se reintenta en el próximo ciclo", e);
        }
        try {
            // Un periodo de validación que se acaba no cierra la postulación: la pasa a
            // esperar a que una persona complete las métricas que no se alimentaron solas.
            validacion.terminarVencidos();
        } catch (Exception e) {
            log.error("El sondeo de validaciones vencidas falló, se reintenta en el próximo ciclo", e);
        }
    }
}

package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Califica las respuestas abiertas de 0 a 4 (RF-55).
 *
 * <p><b>Solo las abiertas.</b> Las preguntas cerradas ya las puntuó el código contra su clave
 * versionada, y el modelo generativo tiene prohibido tocarlas (RF-147): una nota que sale de
 * una tabla no puede depender de que un modelo esté de buen humor. Aquí ni siquiera llegan.
 *
 * <p>Si el candidato no tuvo ninguna pregunta abierta —pasa en las plantillas de Ejecución
 * más cortas— este agente termina sin llamar al modelo. No es un fallo: es que no había nada
 * que calificar, y gastar una llamada para que devuelva una lista vacía no tiene sentido.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteEvaluador implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "EVALUADOR";

    private static final String OBJETIVO = "Calificar de 0 a 4 las respuestas abiertas de la evaluación";

    // Público: ver la nota en AgenteEvidenciaCv
    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "notas": [
                {"respuestaId": <el mismo numero que recibiste, sin cambiarlo>,
                 "puntaje": <numero de 0 a 4>,
                 "explicacion": "<por que esa nota>",
                 "evidenciaCitada": "<la parte literal de su respuesta en que te basas>",
                 "confianza": <numero de 0 a 100>}
              ]
            }
            Una entrada por cada respuesta que recibas. No inventes respuestaId: si no
            reconoces uno, omite esa nota.
            """;

    private final PuenteCalificacionIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoRespuestas insumo = puente.insumoRespuestas(trabajo.getPostulacionId());
        if (insumo.respuestas().isEmpty()) {
            log.info("EVALUADOR: la postulación {} no tiene respuestas abiertas, no hay nada que "
                    + "calificar", trabajo.getPostulacionId());
            return;
        }
        log.info("EVALUADOR califica {} respuestas abiertas de la postulación {}",
                insumo.respuestas().size(), trabajo.getPostulacionId());

        EjecutorAgenteIa.Ejecutado<ResultadoEvaluador> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoEvaluador.class);
        puente.guardarNotasAbiertas(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}

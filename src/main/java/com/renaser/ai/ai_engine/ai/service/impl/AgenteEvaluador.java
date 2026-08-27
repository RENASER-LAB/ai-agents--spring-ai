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

    /**
     * El formato del banco CAZATALENTOS (método CRITERIOS): el modelo NO devuelve puntaje.
     * Declara qué criterios vio y si la respuesta cumple la señal de 0 de su pregunta; el
     * número lo cuenta el código con esas marcas. Así la aritmética no depende del modelo,
     * y los criterios quedan guardados para las banderas del cuestionario completo.
     */
    public static final String FORMATO_CRITERIOS = """
            Cada respuesta trae su C3 ESPERADO, su C4 ESPERADO y su SENAL DE 0. Evalua cada
            una buscando cuatro cosas, presentes o ausentes. No juzgues si la decision fue
            buena: dos jefes buenos resuelven distinto el mismo caso.
            - c1Episodio: ¿cuenta algo que PASO, con momento y lugar identificables? Si solo
              explica como actua "en general", es false.
            - c2Autoria: ¿dice que hizo o decidio EL, en primera persona del singular? Si
              todo es "nosotros" y "se hizo", es false.
            - c3Dato: ¿aparece el dato concreto que el C3 ESPERADO de esa pregunta pide?
            - c4Incomodidad: ¿aparece lo que el C4 ESPERADO de esa pregunta describe?
            - cumpleSenalCero: ¿la respuesta cumple la SENAL DE 0 de esa pregunta?
            Un criterio ausente no se supone presente: si no dijo el plazo, no lo dijo,
            aunque "seguramente lo hizo". Ante la duda, el criterio es false.
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "notas": [
                {"respuestaId": <el mismo numero que recibiste, sin cambiarlo>,
                 "cumpleSenalCero": <true o false>,
                 "c1Episodio": <true o false>,
                 "c2Autoria": <true o false>,
                 "c3Dato": <true o false>,
                 "c4Incomodidad": <true o false>,
                 "explicacion": "<que viste y que falto>",
                 "evidenciaCitada": "<la parte literal de su respuesta en que te basas>",
                 "confianza": <numero de 0 a 100>}
              ]
            }
            No devuelvas ningun campo "puntaje": el puntaje lo calcula el sistema contando
            los criterios. Una entrada por cada respuesta que recibas. No inventes
            respuestaId: si no reconoces uno, omite esa nota.
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

        // El método del banco decide el contrato: en CRITERIOS el modelo declara los
        // criterios y el código cuenta; en el resto devuelve el puntaje de siempre.
        String formato = "CRITERIOS".equals(insumo.metodoCalificacion())
                ? FORMATO_CRITERIOS : FORMATO;
        EjecutorAgenteIa.Ejecutado<ResultadoEvaluador> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, formato, insumo, ResultadoEvaluador.class);
        puente.guardarNotasAbiertas(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}

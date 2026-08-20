package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Califica la prueba del puesto contra su rúbrica (RF-85).
 *
 * <p>Es el hermano del evaluador del hito 2, con una diferencia que lo cambia casi todo:
 * allí la escala era la misma para todos —de 0 a 4—, y aquí <b>cada criterio vale lo que
 * diga su rúbrica</b>, porque cada prueba tiene la suya y la suma de sus puntos es 100. Por
 * eso el máximo viaja con cada criterio en vez de estar escrito en el formato.
 *
 * <p><b>Solo ve la parte de la rúbrica que le toca.</b> Cada criterio declara cómo se
 * verifica (RF-87) y aquí solo llegan los que dicen {@code AGENTE}. Si la rúbrica de una
 * prueba no marca ninguno así, este agente termina sin llamar al modelo: no es un fallo, es
 * que quien escribió la rúbrica decidió que esa prueba la mira una persona entera.
 *
 * <p><b>Puede devolver menos notas de las que se le pidieron, y está bien.</b> Una prueba
 * del puesto se entrega en video, en diapositivas o en un enlace a un repositorio, y de
 * varias de esas cosas no sale texto. Se le pide expresamente que deje fuera lo que no pudo
 * leer, porque un modelo al que se le exige una nota siempre da una nota: el daño no es que
 * se equivoque, es que después nadie puede distinguir la nota fundada de la inventada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentePruebaPuesto implements AgenteSeleccion {

    public static final String CODIGO = "PRUEBA_PUESTO";

    private static final String OBJETIVO = "Calificar la prueba del puesto contra su rúbrica";

    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "criterios": [
                {"codigo": "<el mismo codigo que recibiste, sin cambiarlo>",
                 "puntaje": <numero entre 0 y los puntosMaximos de ese criterio>,
                 "explicacion": "<por que esa nota>",
                 "evidencia": "<la parte literal de la entrega en que te basas>"}
              ],
              "confianza": <numero de 0 a 100>
            }
            Una entrada por cada criterio que puedas calificar con lo que recibiste. Si de
            un criterio no tienes evidencia porque el entregable no se pudo leer, NO lo
            incluyas: lo calificara una persona. No inventes codigos ni agregues criterios
            que no recibiste.
            """;

    private final PuentePruebaIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoPrueba insumo = puente.insumoPrueba(trabajo.getPostulacionId());
        if (insumo.criterios().isEmpty()) {
            log.info("PRUEBA_PUESTO: la rúbrica de la postulación {} no tiene ningún criterio "
                    + "marcado para agente, así que no hay nada que calificar",
                    trabajo.getPostulacionId());
            return;
        }
        log.info("PRUEBA_PUESTO califica {} criterios de la postulación {}, con {} entregas y "
                        + "{} respuestas", insumo.criterios().size(), trabajo.getPostulacionId(),
                insumo.entregas().size(), insumo.respuestas().size());

        EjecutorAgenteIa.Ejecutado<ResultadoPrueba> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoPrueba.class);
        puente.guardarNotasPrueba(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}

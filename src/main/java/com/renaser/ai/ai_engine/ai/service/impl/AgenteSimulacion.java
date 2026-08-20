package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.InsumoConversacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;
import com.renaser.ai.ai_engine.simulacion.service.PuenteSimulacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Prepara las preguntas de la conversación final de la simulación (RF-99).
 *
 * <p><b>No califica nada</b>, y esa es la razón de que exista como agente aparte en vez de
 * ser un campo más del que arma el Perfil de Talento: lo que produce no es un número que
 * entre en una nota, es un guion para una persona. Por eso puede leer cosas que los agentes
 * que puntúan no ven, y por eso su salida se puede tirar y volver a pedir sin que ninguna
 * nota cambie.
 *
 * <p><b>Dónde está el valor.</b> Una pregunta genérica —«cuéntame de una vez que fallaste»—
 * no aporta nada que no estuviera ya en el currículum. Lo que sirve es la que nombra un
 * hecho de esa misma mañana: «lo viste a las 10:41 y lo informaste a las 10:49». Ese hecho
 * el modelo no lo puede inventar, tiene que estar en el insumo, y por eso el insumo son
 * muchas piezas pequeñas —notas, hallazgos, alertas, horas de la sesión— y no un texto: la
 * contradicción vive <b>entre</b> dos de ellas.
 *
 * <p><b>Razona siempre</b>, incluso si el trabajo viniera de una pasada rápida. Encontrar el
 * hueco entre lo que alguien dijo y lo que hizo es justamente deliberar, y aquí no hay tanda
 * que ordenar: son tres o cuatro candidatos que llegaron hasta la simulación, no cien
 * currículums recién caídos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteSimulacion implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "SIMULACION";

    private static final String OBJETIVO =
            "Preparar las preguntas de la conversación final de la simulación";

    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "preguntas": [
                {"texto": "<la pregunta, tal como se le va a leer en voz alta>",
                 "motivo": "<el hecho concreto del que sale, en una oracion>",
                 "alertaId": <el id de la alerta de la que sale, o null>}
              ]
            }
            Entre tres y cinco preguntas, de la mas importante a la menos. No inventes
            alertaId: si la pregunta no sale de ninguna de las alertas que recibiste, pon
            null. No agregues ningun campo mas, ni explicaciones, ni comentarios.
            """;

    private final PuenteSimulacionIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoConversacion insumo = puente.insumoConversacion(trabajo.getPostulacionId());
        log.info("SIMULACION prepara preguntas para la postulación {}: {} hallazgos, {} alertas "
                        + "y {} momentos de la sesión", trabajo.getPostulacionId(),
                insumo.hallazgos().size(), insumo.alertas().size(), insumo.lineaDeTiempo().size());

        // true: aquí no hay pasada rápida que valga. Encontrar una contradicción entre lo
        // que dijo y lo que hizo es exactamente lo que el modelo hace cuando razona.
        EjecutorAgenteIa.Ejecutado<ResultadoConversacion> salida = ejecutor.ejecutar(
                trabajo, OBJETIVO, FORMATO, insumo, ResultadoConversacion.class, true);
        puente.guardarPreguntas(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}

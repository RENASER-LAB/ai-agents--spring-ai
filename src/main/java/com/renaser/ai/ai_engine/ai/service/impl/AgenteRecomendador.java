package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaRecomendacion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Propone criterios y preguntas para las preguntas propias de una vacante (V66).
 *
 * <p>Como el REDACTOR: no trabaja sobre una postulación sino sobre la vacante, y lo que
 * escribe no toca a nadie hasta que una persona lo agrega a su borrador y lo publica.
 *
 * <p><b>Completa lo que falta, no empieza de cero</b>: su propuesta suma exactamente los
 * puntos que le faltan al borrador para llegar a 100, y puede llenar los criterios que ya
 * creó la persona.
 *
 * <p><b>La aduana manda.</b> Lo que devuelve pasa por {@link RecetaRecomendacion}; si no
 * cuadra se le devuelve UNA vez con los errores delante, y si sigue sin cuadrar la propuesta
 * queda fallida y se dice. No se agrega nada a medias.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteRecomendador implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "RECOMENDADOR";

    private static final String OBJETIVO =
            "Proponer criterios y preguntas propias para una vacante";

    public static final String FORMATO = """
            Recibes los datos de la vacante, cuantos puntos tiene que sumar tu propuesta
            (puntosQueFaltan), una indicacion opcional de quien la pidio y los criterios y
            preguntas que ya hay en el borrador, con sus puntos. Completa lo que falta: no
            repitas ninguna pregunta del borrador.
            Reglas de la propuesta:
            - Los puntos son enteros y la suma de los puntos de TODAS tus preguntas es
              exactamente puntosQueFaltan.
            - Cada pregunta va dentro de un criterio. Puedes poner preguntas en un criterio
              que ya existe (usa su id en criterioExistenteId y deja nombre en null) o
              proponer criterios nuevos (criterioExistenteId null, con nombre y queEvalua).
              Un criterio nuevo sin preguntas no vale.
            - Tipos: ABIERTA, OPCION_UNICA, OPCION_MULTIPLE o ESCALA.
            - ABIERTA: sin opciones; di en queDebeTener que debe tener una buena respuesta.
            - OPCION_UNICA: de 2 a 10 opciones con texto; ninguna negativa ni por encima de
              los puntos de la pregunta, y al menos una da exactamente esos puntos.
            - OPCION_MULTIPLE: de 2 a 10 opciones con texto; cada una entre -puntos y
              +puntos (negativas para castigar marcar lo que no va), y la suma de las
              positivas llega a los puntos de la pregunta.
            - ESCALA: de 3 a 10 niveles, del menor al mayor; el texto es un rotulo opcional
              ("Nada", "Mucho"); ninguno negativo ni por encima de los puntos, y al menos
              uno da exactamente esos puntos.
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "criterios": [
                {"criterioExistenteId": <id de un criterio del borrador, o null>,
                 "nombre": "<nombre del criterio nuevo, o null>",
                 "queEvalua": "<que evalua, o null>",
                 "preguntas": [
                   {"tipo": "<ABIERTA | OPCION_UNICA | OPCION_MULTIPLE | ESCALA>",
                    "enunciado": "<la pregunta>",
                    "puntos": <entero>,
                    "queDebeTener": "<solo en las abiertas, o null>",
                    "opciones": [{"texto": "<texto o rotulo>", "puntos": <entero>}]}
                 ]}
              ]
            }
            """;

    private final PuenteRecomendador puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        Long vacanteId = trabajo.getReferenciaId();
        InsumoRecomendador insumo = puente.insumo(vacanteId);
        if (insumo == null) {
            log.info("RECOMENDADOR: la vacante {} no tiene ninguna propuesta pedida esperando",
                    vacanteId);
            return;
        }
        log.info("RECOMENDADOR propone {} puntos de preguntas para la vacante {}",
                insumo.puntosQueFaltan(), vacanteId);

        EjecutorAgenteIa.Ejecutado<ResultadoRecomendador> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoRecomendador.class);
        List<String> errores = RecetaRecomendacion.validar(insumo, salida.resultado());
        if (!errores.isEmpty()) {
            // Una segunda oportunidad con los errores delante. Una sola: pagar a ciegas por el
            // mismo error no arregla nada.
            log.warn("La propuesta de la vacante {} no pasó la aduana ({} errores); se le "
                    + "devuelve al modelo una vez", vacanteId, errores.size());
            salida = ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO + conLosErrores(errores),
                    insumo, ResultadoRecomendador.class);
            errores = RecetaRecomendacion.validar(insumo, salida.resultado());
        }
        if (!errores.isEmpty()) {
            // Fallida y dicha: no se reintenta en la cola, porque sería pagar dos veces más
            // por el mismo error. Quien la pidió ve el porqué y decide.
            puente.marcarFallida(vacanteId, errores);
            return;
        }
        puente.guardarPropuesta(vacanteId, salida.resultado());
    }

    private static String conLosErrores(List<String> errores) {
        return "\nTu intento anterior tuvo estos errores. Corrígelos TODOS y responde de "
                + "nuevo el json completo:\n- " + String.join("\n- ", errores) + "\n";
    }
}

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

    /**
     * El formato de la prueba técnica (V67, V68). Además de lo de siempre: el caso opcional,
     * los entregables con su alcance y la parte calificada de cada criterio, con quién la
     * califica. Nunca «Mira»: lo que mira cada criterio se deduce del alcance.
     */
    public static final String FORMATO_PRUEBA = """
            Recibes los datos de la vacante, cuantos puntos tiene que sumar tu propuesta
            (puntosQueFaltan), una indicacion opcional, el enunciado actual de la prueba (o
            null), su tiempo, sus entregables y sus criterios, cada uno con lo que vale
            (puntos), lo que suman sus preguntas cerradas (puntosDeCerradas) y el resto, su
            parte calificada, con quien la califica.
            Completa lo que falta para una PRUEBA TECNICA: no repitas nada del borrador.
            Como se puntua una prueba:
            - Las preguntas cerradas (OPCION_UNICA, OPCION_MULTIPLE, ESCALA) llevan puntos y
              las cuenta el sistema. Sus reglas: OPCION_UNICA de 2 a 10 opciones, ninguna
              negativa ni por encima de los puntos, y al menos una da exactamente esos puntos;
              OPCION_MULTIPLE de 2 a 10 opciones entre -puntos y +puntos, y las positivas
              llegan a los puntos; ESCALA de 3 a 10 niveles del menor al mayor, ninguno
              negativo ni por encima de los puntos, y al menos uno da exactamente esos puntos.
            - Las ABIERTAS NO llevan puntos (pon 0): en queDebeTener di que debe tener una
              buena respuesta.
            - Cada criterio nuevo dice lo que vale entero en "puntos" (entero): sus cerradas
              mas su parte calificada. Su parte calificada es puntos menos lo que suman sus
              cerradas, nunca negativa, y la califica alguien mirando sus abiertas y los
              archivos de sus preguntas: calificador "IA" o "PERSONA" (null si sus cerradas
              suman todos sus puntos). Un criterio con abiertas necesita parte calificada
              mayor que 0; uno con parte calificada mayor que 0 tiene al menos una abierta o
              mira algun entregable.
            - Los entregables se piden donde se usan. Cada uno es de UNA pregunta de tu
              propuesta ("pregunta": {"criterio": <posicion del criterio desde 0>,
              "pregunta": <posicion de la pregunta desde 0>}), como mucho uno por pregunta; o
              es general y cubre toda la prueba ("todaLaPrueba": true) o algunas preguntas de
              tu propuesta ("cubre": [{"criterio": .., "pregunta": ..}]). Un criterio mira el
              archivo de cada una de sus preguntas y los generales que cubren toda la prueba
              o alguna de sus preguntas: tu no dices que mira cada criterio.
            - Todo entregable lo tiene que mirar al menos un criterio con parte calificada
              mayor que 0: si no, nadie lo califica.
            - La IA no abre enlaces: un criterio que solo mira entregables con formato ENLACE,
              sin abiertas, tiene calificador "PERSONA".
            - Los puntos de tus criterios nuevos suman exactamente puntosQueFaltan.
            - El caso es opcional: proponlo solo si la prueba no tiene enunciado y un escenario
              le sirve.
            - Puedes poner preguntas en un criterio del borrador (criterioExistenteId); entonces
              no propones sus puntos: ese criterio sigue valiendo lo mismo y sus cerradas
              nuevas salen de su parte calificada. No pueden pasarla, y si el criterio tiene
              o recibe abiertas, o mira archivos, le tiene que quedar algo. Esas preguntas no
              suman a puntosQueFaltan.
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "caso": {"enunciado": "<el caso, o null>", "materiales": "<o null>",
                       "herramientasPermitidas": "<o null>"},
              "entregables": [{"nombre": "<...>", "detalle": "<que debe contener>",
                               "formato": "<ARCHIVO | ENLACE | CUALQUIERA>",
                               "obligatorio": <true | false>,
                               "queDebeTener": "<que debe tener una buena entrega>",
                               "pregunta": {"criterio": <entero>, "pregunta": <entero>} o null,
                               "todaLaPrueba": <true | false>,
                               "cubre": [{"criterio": <entero>, "pregunta": <entero>}]}],
              "criterios": [
                {"criterioExistenteId": <id de un criterio del borrador, o null>,
                 "nombre": "<nombre del criterio nuevo, o null>",
                 "queEvalua": "<que evalua, o null>",
                 "puntos": <entero: lo que vale el criterio nuevo entero, o null>,
                 "calificador": "<IA | PERSONA | null>",
                 "preguntas": [
                   {"tipo": "<ABIERTA | OPCION_UNICA | OPCION_MULTIPLE | ESCALA>",
                    "enunciado": "<la pregunta>",
                    "puntos": <entero; 0 en las abiertas>,
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
        if (ColaCalificacionIaImpl.REFERENCIA_PRUEBA.equals(trabajo.getReferenciaTabla())) {
            ejecutarParaLaPrueba(trabajo);
            return;
        }
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

    /** La prueba técnica (V67): misma aduana con sus reglas, y la misma segunda oportunidad. */
    private void ejecutarParaLaPrueba(TrabajoIa trabajo) {
        Long vacanteId = trabajo.getReferenciaId();
        var insumo = puente.insumoPrueba(vacanteId);
        if (insumo == null) {
            log.info("RECOMENDADOR: la vacante {} no tiene ninguna propuesta de prueba pedida", vacanteId);
            return;
        }
        var salida = ejecutor.ejecutar(trabajo, OBJETIVO_PRUEBA, FORMATO_PRUEBA, insumo,
                com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba.class);
        List<String> errores = com.renaser.ai.ai_engine.prueba.service.RecetaRecomendacionPrueba
                .validar(insumo, salida.resultado());
        if (!errores.isEmpty()) {
            log.warn("La propuesta de prueba de la vacante {} no pasó la aduana ({} errores); se le "
                    + "devuelve al modelo una vez", vacanteId, errores.size());
            salida = ejecutor.ejecutar(trabajo, OBJETIVO_PRUEBA, FORMATO_PRUEBA + conLosErrores(errores),
                    insumo, com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba.class);
            errores = com.renaser.ai.ai_engine.prueba.service.RecetaRecomendacionPrueba
                    .validar(insumo, salida.resultado());
        }
        if (!errores.isEmpty()) {
            puente.marcarFallidaPrueba(vacanteId, errores);
            return;
        }
        puente.guardarPropuestaPrueba(vacanteId, salida.resultado());
    }

    private static final String OBJETIVO_PRUEBA = "Proponer el caso, los entregables y los "
            + "criterios de la prueba técnica de una vacante";

    private static String conLosErrores(List<String> errores) {
        return "\nTu intento anterior tuvo estos errores. Corrígelos TODOS y responde de "
                + "nuevo el json completo:\n- " + String.join("\n- ", errores) + "\n";
    }
}

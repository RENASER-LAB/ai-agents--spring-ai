package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.CalificacionEncoladaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PerfilIntegralResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PasadaEncolada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.EvaluacionReabierta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * El Perfil Integral visto desde el panel del equipo.
 *
 * <p>Existe porque hasta ahora la calificación con IA no se podía ni mirar ni relanzar
 * desde fuera: se disparaba sola al entregar la evaluación y el resultado solo se veía
 * consultando la base. Sin estos dos verbos, ningún frontend puede enseñar el retrato de un
 * candidato ni recuperar una calificación que falló.
 *
 * <p>Vive en {@code perfilintegral} y no en {@code postulacion} porque las tablas que lee
 * son de este dominio. El controlador que lo publica es el de postulaciones, que ya está
 * declarado en las dos clases frontera.
 */
public interface ServicioPerfilIntegralPanel {

    /**
     * El retrato de un candidato: notas del currículum, hallazgos y alertas.
     *
     * <p>Nunca falla porque falte el perfil. Si la IA aún no ha corrido devuelve el
     * cascarón con {@code estadoCalificacion} explicando en qué punto está, que es lo que
     * necesita una pantalla para saber si pintar el resultado o el reloj de espera.
     */
    PerfilIntegralResponse ver(ContextoUsuario quien, Long postulacionId);

    /**
     * Pide que se califique, o que se vuelva a calificar.
     *
     * <p>Es idempotente y no bloquea: encola y responde al momento. La llamada al modelo
     * tarda decenas de segundos y no puede colgar una petición HTTP.
     *
     * <p>Sirve para dos cosas distintas: arrancar la calificación de una postulación que
     * se quedó atrás, y recuperar una que falló después de agotar sus reintentos.
     */
    CalificacionEncoladaResponse recalificar(ContextoUsuario quien, Long postulacionId);

    /**
     * Criba: que la IA lea el currículum y arme el retrato con solo eso.
     *
     * <p>Es la primera decisión de una convocatoria: llegan cien currículums y hay que
     * saber a quién invitar a la evaluación. Hasta ahora no había forma de pedirlo, porque
     * {@link #recalificar} exige una evaluación entregada y aquí justamente no la hay.
     *
     * <p>Lo único que hace falta es el currículum. Mueve la postulación a
     * {@code PERFIL_CALIFICANDO} para que el embudo diga la verdad mientras corre, y el
     * agente la deja en {@code PERFIL_POR_CONFIRMAR} con su grupo de prioridad.
     */
    CalificacionEncoladaResponse cribarCv(ContextoUsuario quien, Long postulacionId);

    /**
     * La tanda entera de una convocatoria, ordenada de más apto a menos.
     *
     * <p>Es lo que contesta «¿a quién invito primero?», y no se puede armar desde fuera
     * pidiendo un perfil por candidato: el orden depende del grupo de prioridad, que sale
     * de comparar a todos entre sí.
     *
     * <p>Incluye a quien todavía no tiene nota. Un candidato cuya calificación falló no
     * puede desaparecer de la lista: desaparecería también el problema.
     */
    /**
     * Vuelve a abrir la evaluación de un candidato y le devuelve el turno.
     *
     * <p>Hace falta porque hay 249 evaluaciones <b>vencidas y sin responder</b>: la migración
     * del banco v3 les venció el plazo a propósito —el motor nuevo no sabía puntuar las
     * preguntas del banco viejo y les habría puesto un 0.00 de verdad—. Esos candidatos
     * tienen cuenta, tienen currículum y tienen nota de criba, pero si entran al portal se
     * encuentran con «el plazo para responder ya pasó» y no hay forma de darles otro.
     *
     * <p>Mueve también la postulación a {@code PERFIL_TURNO_CANDIDATO}, y no es un extra: si
     * se le reabre la evaluación pero el estado sigue diciendo que se espera algo de Renaser,
     * el candidato aparece en la bandeja del equipo y su pantalla no le ofrece nada que hacer.
     * El estado contesta «de quién se espera algo», y a partir de aquí se espera de él.
     *
     * <p>Lo que NO hace: tocar una evaluación ya entregada. Reabrir esa borraría el sentido de
     * una nota que alguien ya usó para decidir.
     */
    EvaluacionReabierta reabrirEvaluacion(ContextoUsuario quien, Long postulacionId,
                                          Integer dias, String motivo);

    RankingVacante ranking(ContextoUsuario quien, Long vacanteId);

    /**
     * El mismo ranking, pero con la nota de otra etapa.
     *
     * <p>Las ocho notas del currículum no cambian con la etapa: son del Perfil
     * Integral siempre. Lo que cambia es {@code notaEtapa}, que pasa a ser la de
     * la etapa pedida — quien no la tiene queda al final, como hasta ahora.
     */
    RankingVacante ranking(ContextoUsuario quien, Long vacanteId, String etapaCodigo);

    /**
     * Primera pasada sobre la tanda entera: rápida, para ordenar.
     *
     * <p>Encola a todos los que aún no tienen retrato. Diez currículums tardan medio minuto
     * porque el modelo contesta sin razonar y porque van en paralelo.
     *
     * <p>Lo que sale es un orden, no un veredicto. Sirve para separar la mitad de abajo,
     * donde la decisión es fácil y los dos modelos coinciden.
     */
    PasadaEncolada cribaRapida(ContextoUsuario quien, Long vacanteId);

    /**
     * Segunda pasada, solo sobre los de arriba: cuidadosa, para decidir.
     *
     * <p>Vuelve a calificar con el modelo que razona a la parte alta de la tanda —cuánta,
     * lo dice el parámetro {@code porcentaje_criba_fina}— y pisa las notas provisionales.
     *
     * <p>Se pide después de la primera y no a la vez, porque cuál es «arriba» no se sabe
     * hasta que la primera termina de ordenar a todos.
     */
    PasadaEncolada cribaFina(ContextoUsuario quien, Long vacanteId);

    /**
     * Reemplaza el currículum de una postulación que ya existe, desde el panel.
     *
     * <p>Hace falta porque hasta ahora el currículum solo entraba por el portal, al
     * postular. Sin esto no se puede corregir el de alguien que subió el archivo
     * equivocado, ni el de quien mandó un PDF escaneado del que no sale texto —que es
     * justo el caso en que la IA se planta y no puede calificar.
     *
     * <p>Borra el texto ya extraído y el anonimizado: son de otro archivo y dejarlos sería
     * calificar un currículum con el texto de otro. Se vuelven a sacar en la próxima
     * calificación.
     */
    void reemplazarCv(ContextoUsuario quien, Long postulacionId,
                      org.springframework.web.multipart.MultipartFile archivo);
}

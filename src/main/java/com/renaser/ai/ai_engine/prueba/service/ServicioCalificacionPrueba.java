package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.CalificacionIaEncolada;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DefinirPlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.PonerNotaCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.RespuestaDePrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.math.BigDecimal;
import java.util.List;

/**
 * La calificación de la prueba del puesto, criterio a criterio.
 *
 * <p>A diferencia del hito 2 —donde las preguntas cerradas se puntúan por completo contra
 * una clave, sin que nadie tenga que mirarlas—, la rúbrica de la prueba (RF-85) es
 * mayoritariamente cualitativa: comprensión, calidad, criterio, capacidad de explicar. No hay
 * una fórmula genérica que la calcule sola.
 *
 * <p>Lo que sí es determinístico es <b>ponderar</b> lo que ya se calificó: cada criterio
 * declara sus puntos ({@code criterio.puntos}) y cómo se verifica (RF-87). Este servicio no
 * inventa notas — las suma.
 *
 * <p><b>Quién pone cada nota lo dice la propia rúbrica.</b> Los criterios marcados como
 * verificables por agente los puntúa {@code PRUEBA_PUESTO}; los de persona, una persona. Por
 * eso conviven aquí las dos puertas: {@link #calificarConIa} y {@link #ponerNota}.
 */
public interface ServicioCalificacionPrueba {

    List<NotaCriterioResponse> verNotas(ContextoUsuario quien, Long postulacionId);

    /**
     * Lo que el candidato contestó, pregunta a pregunta y en el orden en que las vio.
     *
     * <p>Hasta ahora sus respuestas solo las podían leer dos: el propio candidato en su
     * portal, y el agente al calificar. Quien revisaba veía la nota y la justificación, pero
     * no el texto que las originó — y una nota que no se puede contrastar con lo que la
     * persona escribió no se puede discutir, solo creer.
     *
     * <p>Salen todas las preguntas de su versión de la plantilla, también las que dejó en
     * blanco: que alguien no contestara la cuarta es justo lo que hay que poder ver.
     */
    List<RespuestaDePrueba> verRespuestas(ContextoUsuario quien, Long postulacionId);

    /**
     * Le pide al agente que califique la parte de la rúbrica que le toca.
     *
     * <p>Solo los criterios marcados como verificables por agente, y solo si la prueba está
     * entregada. Tarda decenas de segundos, así que no devuelve notas: devuelve que quedó
     * pedido, y las notas se consultan después con {@link #verNotas}.
     *
     * <p><b>No lo hace solo al entregar</b>, a propósito: cada llamada al modelo cuesta
     * dinero y a quién se califica lo decide quien lleva la vacante. Es la misma decisión
     * que ya se tomó con la criba de currículums.
     *
     * @throws IllegalStateException si la prueba todavía no está entregada
     */
    CalificacionIaEncolada calificarConIa(ContextoUsuario quien, Long postulacionId);

    void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId, PonerNotaCriterio datos);

    /**
     * Pondera las notas ya puestas y guarda la nota de la etapa PRUEBA_PUESTO.
     *
     * @throws IllegalStateException si falta la nota de algún criterio de la rúbrica
     */
    BigDecimal calcularNotaEtapa(ContextoUsuario quien, Long postulacionId);

    /**
     * Le fija a UN candidato la fecha en que se le cierra la prueba.
     *
     * <p>Hasta ahora el plazo solo se podía decir en la plantilla, en días, y contados desde
     * que cada uno empieza: dos personas invitadas el mismo día terminaban con dos fechas
     * distintas, y no había forma de decir «todos hasta el domingo». Esto la fija.
     *
     * <p>Se puede poner <b>antes o después</b> de que empiece. Si se pone antes, empezar ya
     * no la recalcula — la fecha puesta a mano manda—; si se pone después, reemplaza a la que
     * el reloj había calculado, que es como se le dan más horas a quien las pide.
     *
     * @throws IllegalStateException si la prueba ya está entregada: mover el plazo de algo
     *                               que ya se entregó no cambia nada y engaña al que lo mira
     */
    PlazoPrueba definirPlazo(ContextoUsuario quien, Long postulacionId, DefinirPlazoPrueba datos);
}

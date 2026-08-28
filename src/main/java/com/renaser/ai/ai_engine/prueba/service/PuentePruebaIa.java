package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;

/**
 * La única puerta entre el motor de agentes y la prueba del puesto.
 *
 * <p><b>Por qué existe.</b> Es la misma frontera que ya guarda {@code PuenteCalificacionIa}
 * para el Perfil Integral: el agente vive bajo {@code ai/} y las tablas que escribe
 * —{@code nota_criterio}, {@code nota_etapa}— son del módulo de selección, que mantiene otra
 * persona. Si el agente usara los repositorios de aquí directamente, la frontera entre los
 * dos módulos dejaría de existir.
 *
 * <p>Con esta interfaz el motor de agentes solo sabe dos verbos: <b>pedir la entrega</b> de
 * un candidato y <b>entregar las notas</b>. Qué tabla, qué versión de plantilla y qué estado
 * sigue se decide de este lado.
 *
 * <p><b>Reglas que se cumplen aquí y no en el agente</b>, porque son del negocio:
 * <ul>
 *   <li>Solo se le enseñan los criterios que la rúbrica marca como verificables por agente.
 *   <li>Una nota sin explicación no se guarda (RF-150).
 *   <li>Un ajuste hecho a mano nunca se pisa.
 *   <li>La postulación solo se mueve con {@code MaquinaEstados}.
 * </ul>
 */
public interface PuentePruebaIa {

    /**
     * La entrega del candidato y la parte de la rúbrica que le toca al agente.
     *
     * <p><b>Puede venir sin criterios, y eso no es un error</b>: significa que la rúbrica de
     * esa prueba no tiene ninguno marcado como {@code AGENTE}, así que la califica una
     * persona entera. El agente lo mira y termina sin llamar al modelo.
     *
     * @throws IllegalStateException si esta postulación no tiene prueba, o si la tiene sin
     *                               entregar. Calificar una prueba a medias daría una nota
     *                               que no vale nada
     */
    InsumoPrueba insumoPrueba(Long postulacionId);

    /**
     * Guarda las notas del modelo, deja la prueba en {@code PRUEBA_POR_CONFIRMAR} y, si la
     * rúbrica quedó entera, calcula también la nota de la etapa.
     *
     * <p><b>Solo entera.</b> Si a algún criterio le falta el puntaje no se suma nada, y el
     * registro dice cuál falta: sumar media rúbrica daría una nota baja que parece un juicio
     * y es un hueco. Los de método {@code PERSONA} siguen vacíos por diseño, así que lo
     * normal es que no se sume — pero una rúbrica de puros criterios de agente sí queda
     * completa aquí, y antes se quedaba sin nota esperando a que alguien la pidiera desde el
     * panel.
     *
     * <p>El resto del razonamiento —por qué se comprueba antes de sumar en vez de intentar y
     * atrapar, y por qué se pondera aunque la postulación ya se haya movido— vive donde se
     * decide, en la implementación.
     */
    void guardarNotasPrueba(Long postulacionId, Long ejecucionIaId, ResultadoPrueba resultado);
}

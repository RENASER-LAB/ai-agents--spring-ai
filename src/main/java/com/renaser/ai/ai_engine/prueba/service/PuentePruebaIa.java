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
     * Guarda las notas y deja la prueba lista para que una persona la confirme.
     *
     * <p><b>No calcula la nota de la etapa.</b> Eso lo hace {@code calcularNotaEtapa} cuando
     * están todos los criterios, y aquí casi nunca lo están: los de método {@code PERSONA}
     * siguen vacíos y los que el modelo no pudo juzgar, también. Sumar media rúbrica daría
     * una nota baja que parece un juicio y es un hueco.
     */
    void guardarNotasPrueba(Long postulacionId, Long ejecucionIaId, ResultadoPrueba resultado);
}

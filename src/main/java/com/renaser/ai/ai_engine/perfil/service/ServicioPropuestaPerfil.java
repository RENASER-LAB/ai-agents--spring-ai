package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;

/**
 * Lleva al perfil lo que el agente DATOS_CV leyó de un currículum.
 *
 * <p><b>Propone, no escribe encima.</b> Es la regla que más importa de todo el perfil
 * (RF-159): lo leído entra marcado como CURRICULUM y sin confirmar, y lo que la persona
 * escribió o confirmó no se toca nunca. Una extracción que pise un dato corregido a mano
 * convierte una herramienta útil en una que hay que vigilar.
 */
public interface ServicioPropuestaPerfil {

    /**
     * Propone al perfil de la persona dueña de la postulación lo que se leyó de su
     * currículum. Crea el perfil si no existía. Si la persona fue anonimizada (borrado
     * de la ley 29733), no hace nada: sería resucitar datos que se pidieron borrar.
     */
    void proponer(Long postulacionId, ResultadoDatos resultado);

    /**
     * Lo mismo, pero a partir de la persona: para el currículum que subió a <b>su perfil</b>,
     * que no tiene postulación detrás y puede que nunca la tenga.
     *
     * <p>Las reglas son exactamente las mismas —propone, no pisa, y no resucita a quien pidió
     * el borrado—; lo único que cambia es de dónde sale la persona.
     *
     * @return true si de verdad entró algo al perfil. Un {@code false} es lo que convierte la
     *         lectura en {@code NO_LEGIBLE}: decir {@code LISTA} sobre una ficha vacía haría
     *         que la pantalla prometiera datos que no están.
     */
    boolean proponerAlPerfil(Long personaId, ResultadoDatos resultado);

    /**
     * Si el perfil de esta persona todavía conserva algo que le propuso un currículum.
     *
     * <p>Lo pregunta quien decide si una lectura ya pagada sirve de recibo. La ficha de una
     * postulación ({@code dato_cv}) dice que ese archivo se leyó, pero no guarda lo que se
     * propuso: si el perfil se borró entre medias —el barrido de retención se lleva los
     * perfiles dormidos—, aquellas propuestas ya no están y el recibo no vale para nada.
     *
     * @return true si queda al menos una fila con origen CURRICULUM. Se mira eso y no si el
     *         perfil tiene algo: lo que la persona escribió a mano no lo puso ningún
     *         currículum, así que no prueba que aquella lectura siga sirviendo.
     */
    boolean conservaLoPropuestoDeUnCurriculum(Long personaId);

    /**
     * Propone al perfil los enlaces que el candidato escribió en el formulario de postular.
     * Un enlace que no valida (RF-166) se omite sin romper la postulación: el formulario de
     * postular no es el sitio para pelear por una URL.
     */
    void proponerEnlaces(Long personaId, String linkedin, String github, String portafolio);
}

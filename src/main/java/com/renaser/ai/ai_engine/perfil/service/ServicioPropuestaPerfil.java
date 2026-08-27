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
     * Propone al perfil los enlaces que el candidato escribió en el formulario de postular.
     * Un enlace que no valida (RF-166) se omite sin romper la postulación: el formulario de
     * postular no es el sitio para pelear por una URL.
     */
    void proponerEnlaces(Long personaId, String linkedin, String github, String portafolio);
}

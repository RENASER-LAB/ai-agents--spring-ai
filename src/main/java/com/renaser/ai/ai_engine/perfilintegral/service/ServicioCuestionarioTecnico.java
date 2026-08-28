package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CorregirPreguntaTecnica;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CuestionarioResponse;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

// El cuestionario técnico visto desde el panel del dueño: pedir la generación, revisar el
// borrador, corregirlo con sus palabras y publicarlo. La IA propone, la persona publica.
public interface ServicioCuestionarioTecnico {

    /**
     * Encola al REDACTOR. Exige la ficha COMPLETA.
     *
     * @return true si quedó encolado; false si ya hay una generación en curso o la IA
     *         está apagada — el panel lo dice tal cual, sin prometer lo que no encoló.
     */
    boolean generar(ContextoUsuario quien, Long vacanteId);

    /** El cuestionario de la vacante: el borrador si hay, si no la publicada, con el
     *  estado de la generación y si quedó desactualizado respecto a la ficha. */
    CuestionarioResponse ver(ContextoUsuario quien, Long vacanteId);

    /** Corregir una pregunta del borrador con las palabras del dueño. Solo en BORRADOR. */
    void corregirPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                          CorregirPreguntaTecnica datos);

    /** Publicar el borrador: el acto humano que vuelve real el cuestionario. */
    void publicar(ContextoUsuario quien, Long vacanteId);
}

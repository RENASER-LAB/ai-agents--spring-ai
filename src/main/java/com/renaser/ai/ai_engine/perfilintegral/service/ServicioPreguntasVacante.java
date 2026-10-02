package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * Las preguntas propias de una vacante (V66, fase 1): el editor agrupado por criterios, la
 * publicación, lo que se puede cambiar con candidatos dentro, la copia entre vacantes de la
 * misma empresa, las recomendaciones de la IA y el ajuste a mano de una abierta.
 *
 * <p>Lo que comparte con el editor de la prueba del puesto está en {@link EditorDeLaVacante}.
 * Su vara se congela en la primera postulación.
 */
public interface ServicioPreguntasVacante extends EditorDeLaVacante {

    /** Para la sección de la vacante: «Sin preguntas», «Borrador · 68 de 100 puntos»… */
    ResumenDePreguntas resumenDe(Long vacanteId);

    // ---------- El borrador ----------

    EditorDePreguntas guardarDatosDelBorrador(ContextoUsuario quien, Long vacanteId,
                                              GuardarDatosDelBorrador datos);

    EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId, GuardarCriterio datos);

    EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                     GuardarCriterio datos);

    /** Sin ningún criterio creado, la pregunta nace dentro de uno «General». */
    EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId, GuardarPregunta datos);

    EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                     GuardarPregunta datos);

    // ---------- Con la versión publicada ----------

    /** La guía, el «qué evalúa» y el «qué debe tener». Recalifica a quien ya tiene nota. */
    CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                         CorregirInstrucciones datos);

    /** Los puntos de preguntas, opciones y niveles. Recalcula a todos al instante, sin IA. */
    CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntos datos);

    // ---------- Recomendaciones por IA ----------

    EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId,
                                           Long propuestaId, AgregarDeLaPropuesta datos);

    // ---------- Ajustar a mano una abierta ----------

    void ajustarNota(ContextoUsuario quien, Long postulacionId, Long respuestaId, AjustarNota datos);
}

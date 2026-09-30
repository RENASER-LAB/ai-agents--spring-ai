package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * Las preguntas propias de una vacante (V66, fase 1): el editor agrupado por criterios, la
 * publicación, lo que se puede cambiar con candidatos dentro, la copia entre vacantes de la
 * misma empresa, las recomendaciones de la IA y el ajuste a mano de una abierta.
 *
 * <p>Todo entra por la vacante y su organización: lo de otra empresa contesta 404. Cada
 * cambio del borrador devuelve el editor entero, recalculado por el servidor: el panel lo
 * pinta tal cual y no parchea nada a mano.
 */
public interface ServicioPreguntasVacante {

    EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId);

    /** Para la sección de la vacante: «Sin preguntas», «Borrador · 68 de 100 puntos»… */
    ResumenDePreguntas resumenDe(Long vacanteId);

    // ---------- El borrador ----------

    /**
     * Abre el borrador si no lo hay: vacío, o como copia de la versión publicada mientras
     * nadie haya postulado. Es lo que permite corregir una versión publicada antes de que
     * la rinda nadie.
     */
    EditorDePreguntas abrirBorrador(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas guardarDatosDelBorrador(ContextoUsuario quien, Long vacanteId,
                                              GuardarDatosDelBorrador datos);

    EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId, GuardarCriterio datos);

    EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                     GuardarCriterio datos);

    /** Sus preguntas quedan sin criterio hasta que se muevan; mientras, no se publica. */
    EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId);

    EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                    Mover datos);

    /** Sin ningún criterio creado, la pregunta nace dentro de uno «General». */
    EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId, GuardarPregunta datos);

    EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                     GuardarPregunta datos);

    EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId);

    EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                    Mover datos);

    /** Exige 100 puntos y todo lo demás; si falta algo, 400 con la lista entera. */
    EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId);

    // ---------- Con la versión publicada ----------

    /** La guía, el «qué evalúa» y el «qué debe tener». Recalifica a quien ya tiene nota. */
    CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                         CorregirInstrucciones datos);

    /** Vuelve a encolar solo a quienes se quedaron con la nota de una guía anterior. */
    CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId);

    /** Los puntos de preguntas, opciones y niveles. Recalcula a todos al instante, sin IA. */
    CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntos datos);

    // ---------- Copiar de otra vacante ----------

    List<VacanteCopiable> copiables(ContextoUsuario quien, Long vacanteId, String buscar,
                                    String nivel);

    VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId, Long vacanteOrigenId);

    EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos);

    // ---------- Recomendaciones por IA ----------

    RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                             PedirRecomendaciones datos);

    EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId,
                                           Long propuestaId, AgregarDeLaPropuesta datos);

    // ---------- Ajustar a mano una abierta ----------

    void ajustarNota(ContextoUsuario quien, Long postulacionId, Long respuestaId, AjustarNota datos);
}

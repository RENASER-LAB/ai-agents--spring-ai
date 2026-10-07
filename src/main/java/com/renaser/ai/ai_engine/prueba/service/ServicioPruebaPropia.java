package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.service.EditorDeLaVacante;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.FijarFechaLimite;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

/**
 * La prueba técnica de una vacante escrita en el editor (V67, fase 2): el borrador agrupado
 * por criterios con el caso, el tiempo y los entregables; la publicación; lo que se puede
 * cambiar con candidatos dentro; la copia entre vacantes de la misma empresa y las
 * recomendaciones de la IA.
 *
 * <p>Es el editor de la fase 1 ({@code ServicioPreguntasVacante}) con un propósito distinto
 * ({@code PRUEBA_PUESTO}) y lo que le falta a una prueba: lo que comparten está en
 * {@link EditorDeLaVacante}. <b>No se unifica la calificación</b>
 * con la de las preguntas propias (decisión 3): aquí las abiertas y los entregables no llevan
 * puntos y la IA califica el criterio entero.
 *
 * <p>Dos diferencias de frontera con la fase 1:
 * <ul>
 *   <li><b>La vara se congela en la primera rendición</b>, no en la primera postulación
 *       (decisión 9): hasta que alguien abra la prueba se puede publicar otra versión.
 *   <li>Lo de otra empresa contesta 404, como en todo el panel.
 * </ul>
 */
public interface ServicioPruebaPropia extends EditorDeLaVacante {

    /** Para el bloque «Prueba técnica» de la vacante y la regla de publicarla. */
    ResumenDePreguntas resumenDe(Long vacanteId);

    // ---------- El borrador ----------

    EditorDePreguntas guardarDatos(ContextoUsuario quien, Long vacanteId, GuardarDatosDeLaPrueba datos);

    EditorDePreguntas subirConsigna(ContextoUsuario quien, Long vacanteId, MultipartFile archivo);

    EditorDePreguntas quitarConsigna(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId, GuardarCriterioDePrueba datos);

    EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                     GuardarCriterioDePrueba datos);

    EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId, GuardarPreguntaDePrueba datos);

    EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                     GuardarPreguntaDePrueba datos);

    EditorDePreguntas agregarEntregable(ContextoUsuario quien, Long vacanteId, GuardarEntregable datos);

    EditorDePreguntas editarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId,
                                       GuardarEntregable datos);

    /** Desaparece también de lo que miran los criterios (el panel ya pidió confirmarlo). */
    EditorDePreguntas quitarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId);

    EditorDePreguntas moverEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId, Mover datos);

    /**
     * La fecha límite para dar la prueba (V68): la de la vacante. Se puede poner antes de
     * publicar; no se acepta una pasada; mueve los intentos abiertos sin plazo propio; y con
     * la prueba publicada y alguien en la etapa técnica pide un motivo, que se audita.
     */
    EditorDePreguntas fijarFechaLimite(ContextoUsuario quien, Long vacanteId, FijarFechaLimite datos);

    // ---------- Con la versión publicada ----------

    CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                         CorregirInstruccionesDePrueba datos);

    CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntosDePrueba datos);

    // ---------- Recomendaciones por IA ----------

    EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId, Long propuestaId,
                                           AgregarDeLaPropuestaDePrueba datos);
}

package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * La prueba técnica de una vacante escrita en el editor (V67, fase 2): el borrador agrupado
 * por criterios con el caso, el tiempo y los entregables; la publicación; lo que se puede
 * cambiar con candidatos dentro; la copia entre vacantes de la misma empresa y las
 * recomendaciones de la IA.
 *
 * <p>Es el editor de la fase 1 ({@code ServicioPreguntasVacante}) con un propósito distinto
 * ({@code PRUEBA_PUESTO}) y lo que le falta a una prueba. <b>No se unifica la calificación</b>
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
public interface ServicioPruebaPropia {

    EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId);

    /** Para el bloque «Prueba técnica» de la vacante y la regla de publicarla. */
    ResumenDePreguntas resumenDe(Long vacanteId);

    // ---------- El borrador ----------

    EditorDePreguntas abrirBorrador(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas guardarDatos(ContextoUsuario quien, Long vacanteId, GuardarDatosDeLaPrueba datos);

    EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas subirConsigna(ContextoUsuario quien, Long vacanteId, MultipartFile archivo);

    EditorDePreguntas quitarConsigna(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas agregarCriterio(ContextoUsuario quien, Long vacanteId, GuardarCriterioDePrueba datos);

    EditorDePreguntas editarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                     GuardarCriterioDePrueba datos);

    EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId);

    EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId, Mover datos);

    EditorDePreguntas agregarPregunta(ContextoUsuario quien, Long vacanteId, GuardarPreguntaDePrueba datos);

    EditorDePreguntas editarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                     GuardarPreguntaDePrueba datos);

    EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId);

    EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId, Mover datos);

    EditorDePreguntas agregarEntregable(ContextoUsuario quien, Long vacanteId, GuardarEntregable datos);

    EditorDePreguntas editarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId,
                                       GuardarEntregable datos);

    /** Desaparece también de lo que miran los criterios (el panel ya pidió confirmarlo). */
    EditorDePreguntas quitarEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId);

    EditorDePreguntas moverEntregable(ContextoUsuario quien, Long vacanteId, Long entregableId, Mover datos);

    /** Exige todo lo del punto 3; si falta algo, 400 con la lista entera. */
    EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId);

    // ---------- Con la versión publicada ----------

    CambioAplicado corregirInstrucciones(ContextoUsuario quien, Long vacanteId,
                                         CorregirInstruccionesDePrueba datos);

    CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId);

    CambioAplicado cambiarPuntos(ContextoUsuario quien, Long vacanteId, CambiarPuntosDePrueba datos);

    // ---------- Copiar de otra vacante ----------

    List<VacanteCopiable> copiables(ContextoUsuario quien, Long vacanteId, String buscar, String nivel);

    VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId, Long vacanteOrigenId);

    EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos);

    // ---------- Recomendaciones por IA ----------

    RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                             PedirRecomendaciones datos);

    EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas agregarDeLaPropuesta(ContextoUsuario quien, Long vacanteId, Long propuestaId,
                                           AgregarDeLaPropuestaDePrueba datos);
}

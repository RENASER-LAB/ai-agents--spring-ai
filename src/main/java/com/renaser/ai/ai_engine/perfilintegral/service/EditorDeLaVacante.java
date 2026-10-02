package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * Lo que los dos editores de una vacante hacen igual: el de sus preguntas propias
 * ({@link ServicioPreguntasVacante}, V66) y el de su prueba del puesto (V67). Los dos guardan
 * un borrador y una versión publicada por vacante y propósito, agrupados por criterios.
 *
 * <p>Todo entra por la vacante y su organización: lo de otra empresa contesta 404. Cada cambio
 * del borrador devuelve el editor entero, recalculado por el servidor: el panel lo pinta tal
 * cual y no parchea nada a mano.
 */
public interface EditorDeLaVacante {

    EditorDePreguntas ver(ContextoUsuario quien, Long vacanteId);

    // ---------- El borrador ----------

    /**
     * Abre el borrador si no lo hay: vacío, o como copia de la versión publicada mientras su
     * vara se pueda mover. Es lo que permite corregir una versión publicada antes de que la
     * rinda nadie.
     */
    EditorDePreguntas abrirBorrador(ContextoUsuario quien, Long vacanteId);

    EditorDePreguntas descartarBorrador(ContextoUsuario quien, Long vacanteId);

    /** Sus preguntas quedan sin criterio hasta que se muevan; mientras, no se publica. */
    EditorDePreguntas quitarCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId);

    EditorDePreguntas moverCriterio(ContextoUsuario quien, Long vacanteId, Long criterioId,
                                    Mover datos);

    EditorDePreguntas quitarPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId);

    EditorDePreguntas moverPregunta(ContextoUsuario quien, Long vacanteId, Long preguntaId,
                                    Mover datos);

    /** Exige 100 puntos y todo lo demás; si falta algo, 400 con la lista entera. */
    EditorDePreguntas publicar(ContextoUsuario quien, Long vacanteId);

    // ---------- Con la versión publicada ----------

    /** Vuelve a encolar solo a quienes se quedaron con la nota de una guía anterior. */
    CambioAplicado reintentarRecalificacion(ContextoUsuario quien, Long vacanteId);

    // ---------- Copiar de otra vacante ----------

    List<VacanteCopiable> copiables(ContextoUsuario quien, Long vacanteId, String buscar,
                                    String nivel);

    VersionDePreguntas vistaPrevia(ContextoUsuario quien, Long vacanteId, Long vacanteOrigenId);

    EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos);

    // ---------- Recomendaciones por IA ----------

    RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                             PedirRecomendaciones datos);

    EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId);
}

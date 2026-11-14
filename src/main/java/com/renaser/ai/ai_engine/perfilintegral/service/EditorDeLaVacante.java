package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.VacanteDestino;
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

    /**
     * Copia la publicada de otra vacante en un borrador de esta. La destino tiene que usar el
     * editor para esto (las preguntas propias o la prueba del editor): si no, 409 y nada cambia.
     */
    EditorDePreguntas copiar(ContextoUsuario quien, Long vacanteId, CopiarDeOtraVacante datos);

    // ---------- «Pruebas»: el índice de la empresa ----------

    /**
     * Las vacantes de la empresa a las que llega {@code ver_vacantes} y que tienen su versión
     * escrita en este editor, publicada o en borrador, ya filtradas y en orden. Solo las que usan
     * el editor para esto: lo del sistema antiguo y las versiones que quedaron en una vacante que
     * ya no las usa no salen.
     */
    List<FilaDePrueba> indice(ContextoUsuario quien, FiltrosDelIndice filtros);

    /**
     * La vista previa de una fila del índice: la publicada o, si no la hay, el borrador, sin
     * sus avisos (son del editor). Lo ajeno, lo eliminado y lo que no está en el índice es 404.
     */
    VersionDePreguntas vistaPreviaDelIndice(ContextoUsuario quien, Long vacanteId);

    /**
     * Las vacantes que podrían recibir una copia: las editables a las que llega
     * {@code editar_vacante} y que usan el editor para esto. Las que ya rinde alguien salen
     * con su motivo. {@code excluir} (opcional) es la vacante de origen.
     */
    List<VacanteDestino> destinos(ContextoUsuario quien, Long excluir);

    // ---------- Recomendaciones por IA ----------

    RecomendacionPedida pedirRecomendaciones(ContextoUsuario quien, Long vacanteId,
                                             PedirRecomendaciones datos);

    EstadoDeLaRecomendacion comoVaLaRecomendacion(ContextoUsuario quien, Long vacanteId);
}

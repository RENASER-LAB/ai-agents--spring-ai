package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirResena;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.LaResenaDeMiEmpresa;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasDeLaPostulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * Las reseñas en la ficha del postulante: leerlas, y escribir la de mi empresa.
 *
 * <p>Todo entra por una postulación DE LA EMPRESA de quien pregunta: lo ajeno responde 404
 * sin confirmar que existe. La lectura entre empresas —las reseñas que otras empresas le
 * dejaron a esta persona— es la única excepción, y se hace por la persona de esa postulación.
 */
public interface ServicioResenasPanel {

    /** Las reseñas de la persona y, si toca, el bloque de la reseña de mi empresa. */
    ResenasDeLaPostulacion ver(ContextoUsuario quien, Long postulacionId);

    /** Publicar la reseña de una contratación de mi empresa, cumplido su primer mes. */
    LaResenaDeMiEmpresa publicar(ContextoUsuario quien, Long postulacionId, EscribirResena datos);

    /** Editarla dentro de los 30 días desde su primera publicación. */
    LaResenaDeMiEmpresa editar(ContextoUsuario quien, Long postulacionId, EscribirResena datos);

    /** Borrarla dentro de su plazo: la contratación vuelve a quedar libre. */
    LaResenaDeMiEmpresa borrar(ContextoUsuario quien, Long postulacionId);

    /** Reportar la respuesta de la persona a la reseña de mi empresa. */
    LaResenaDeMiEmpresa reportarRespuesta(ContextoUsuario quien, Long postulacionId,
                                          Reportar datos);
}

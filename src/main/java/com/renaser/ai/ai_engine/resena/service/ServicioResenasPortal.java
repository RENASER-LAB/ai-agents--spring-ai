package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirRespuesta;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.MisResenas;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasEnLaDescarga;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * Las reseñas desde el perfil de la persona reseñada: leerlas, reportarlas y responderlas.
 *
 * <p>No hay permiso que valga aquí: lo que decide es de quién es la reseña, y eso se
 * comprueba contra la persona que pregunta. Una reseña ajena responde 404, igual que una que
 * no existe.
 */
public interface ServicioResenasPortal {

    /** El promedio, la cantidad y el reparto de sus reseñas visibles: la cabecera. */
    ResumenResenas resumen(Long personaId);

    /** La sección «Reseñas de empresas» y la ventana «Ver todas». */
    MisResenas mias(ContextoUsuario quien);

    /** Reportar una reseña suya. Sigue visible y contando mientras se revisa. */
    void reportar(ContextoUsuario quien, Long resenaId, Reportar datos);

    /** Responder a una reseña suya: una respuesta por reseña, sin hilo. */
    void responder(ContextoUsuario quien, Long resenaId, EscribirRespuesta datos);

    /** Editar su respuesta dentro del plazo. */
    void editarRespuesta(ContextoUsuario quien, Long resenaId, EscribirRespuesta datos);

    /** Borrar su respuesta dentro del plazo: puede volver a responder. */
    void borrarRespuesta(ContextoUsuario quien, Long resenaId);

    /** Lo que la descarga de sus datos lleva de las reseñas. */
    ResenasEnLaDescarga paraLaDescarga(Long personaId);

    /**
     * El borrado de datos de la ley 29733: sus reseñas, sus respuestas y los reportes dejan
     * de existir. La auditoría conserva que los hubo, sin su texto.
     *
     * @return cuántas reseñas y respuestas se llevó, para la auditoría del borrado
     */
    java.util.Map<String, Integer> borrarDeLaPersona(Long personaId);
}

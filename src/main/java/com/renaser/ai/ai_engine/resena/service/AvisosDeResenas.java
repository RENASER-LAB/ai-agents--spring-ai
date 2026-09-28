package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.resena.entity.Resena;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Lo que la campana del portal le cuenta a la persona de sus reseñas. Solo campana, sin
 * correo.
 *
 * <p>Los avisos no enlazan a ninguna postulación: la reseña es de la persona, no del proceso,
 * y un punto en la fila de «mis procesos» diría que ahí pasó algo. El portal los reconoce
 * por el tipo y los lleva a la sección de reseñas del perfil.
 *
 * <p>⚠️ Publicar un aviso nunca tumba lo que lo provocó: {@link ServicioAvisosPortal} ya
 * atrapa lo suyo, y aquí se atrapa también lo que pudiera escaparse por el proxy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvisosDeResenas {

    private final ServicioAvisosPortal avisos;

    /** «[Empresa] te dejó una reseña». No se avisa de las ediciones, salvo si ya respondió. */
    public void publicada(Resena resena, String empresa, Long usuarioId) {
        publicar(resena, usuarioId, AvisoPortal.RESENA_PUBLICADA,
                empresa + " te dejó una reseña",
                "Puedes leerla en tu perfil y, si quieres, responderla.");
    }

    /** «[Empresa] editó su reseña. Puedes revisar tu respuesta hasta el [fecha]». */
    public void editadaYaRespondida(Resena resena, String empresa, Long usuarioId,
                                    Instant hasta) {
        publicar(resena, usuarioId, AvisoPortal.RESENA_EDITADA,
                empresa + " editó su reseña",
                "Puedes revisar tu respuesta hasta el " + ReglasDeLaResena.fecha(hasta) + ".");
    }

    /** «Revisamos tu reporte: la ocultamos» o «… : la mantuvimos». */
    public void reporteResuelto(Resena resena, String empresa, Long usuarioId,
                                boolean laOcultamos) {
        if (laOcultamos) {
            publicar(resena, usuarioId, AvisoPortal.REPORTE_RESENA_RESUELTO,
                    "Revisamos tu reporte: la ocultamos",
                    "La reseña de " + empresa + " ya no se ve en tu perfil ni la ven las "
                            + "empresas donde te postulas.");
        } else {
            publicar(resena, usuarioId, AvisoPortal.REPORTE_RESENA_RESUELTO,
                    "Revisamos tu reporte: la mantuvimos",
                    "La reseña de " + empresa + " cumple las normas y sigue visible.");
        }
    }

    /** «La plataforma ocultó tu respuesta a la reseña de [Empresa]». */
    public void respuestaOcultada(Resena resena, String empresa, Long usuarioId) {
        publicar(resena, usuarioId, AvisoPortal.RESPUESTA_RESENA_OCULTADA,
                "La plataforma ocultó tu respuesta a la reseña de " + empresa,
                "Las empresas ya no la ven. En tu perfil puedes leer el motivo.");
    }

    private void publicar(Resena resena, Long usuarioId, String tipo, String titulo,
                          String cuerpo) {
        if (usuarioId == null) {
            return;
        }
        try {
            avisos.publicar(resena.getOrganizacionId(), usuarioId, tipo, titulo, cuerpo,
                    null, null);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar «{}» de la reseña {}: {}", tipo, resena.getId(),
                    e.getMessage());
        }
    }
}

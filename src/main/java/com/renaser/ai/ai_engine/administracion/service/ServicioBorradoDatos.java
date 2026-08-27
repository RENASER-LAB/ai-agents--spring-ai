package com.renaser.ai.ai_engine.administracion.service;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.SolicitudBorradoPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * El borrado de datos de la ley 29733: la bandeja de solicitudes pendientes y su
 * ejecución. Vive aparte del resto de la administración porque es el código más
 * destructivo del sistema —anonimiza personas, borra archivos físicos y cierra
 * postulaciones— y merece que nadie lo toque por accidente al editar un parámetro.
 */
public interface ServicioBorradoDatos {

    List<SolicitudBorradoPanel> solicitudesBorradoPendientes(ContextoUsuario quien);

    // La anonimización: vacía a la persona, conserva la trazabilidad
    void ejecutarBorrado(ContextoUsuario quien, Long solicitudId);
}

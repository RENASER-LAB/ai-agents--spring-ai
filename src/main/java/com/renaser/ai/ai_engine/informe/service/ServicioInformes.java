package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * Los informes por etapa del panel: el estado de cada uno, el informe como página (el visor) y
 * como PDF, los tres de un postulante y los de toda la vacante en un .zip.
 *
 * <p>Todo es de solo lectura y se arma cada vez que se pide: no se guarda ningún PDF. Cada
 * informe pide el permiso de la pantalla que enseña esos datos (ver {@link EtapaDelInforme}):
 * sin él, 403; con un candidato de otra empresa o fuera del alcance, 404.
 */
public interface ServicioInformes {

    /** Qué informes de esta postulación puede ver quien pregunta, y si ya se pueden descargar. */
    InformesDelPostulante estados(ContextoUsuario quien, Long postulacionId);

    /** El informe de una etapa, para el visor. 409 si todavía no hay nada que enseñar. */
    Informe informe(ContextoUsuario quien, Long postulacionId, String etapa);

    /** El mismo informe, en PDF. */
    ArchivoArmado pdf(ContextoUsuario quien, Long postulacionId, String etapa);

    /** Los informes disponibles de un postulante en un .zip, con un aviso de los que faltan. */
    ArchivoArmado zipDelPostulante(ContextoUsuario quien, Long postulacionId);

    /** La lista del visor: los postulantes de la vacante en el orden del ranking. */
    VisorDeLaVacante visor(ContextoUsuario quien, Long vacanteId);

    /** Los informes de toda la vacante: una carpeta por postulante y un aviso de los que faltan. */
    ArchivoArmado zipDeLaVacante(ContextoUsuario quien, Long vacanteId);
}

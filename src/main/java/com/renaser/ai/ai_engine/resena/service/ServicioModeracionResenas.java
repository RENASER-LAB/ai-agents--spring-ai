package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.ReporteParaModerar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResolverReporte;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * «Reseñas reportadas»: la plataforma revisa lo que la persona reporta de una reseña y lo
 * que la empresa autora reporta de una respuesta, en las mismas listas.
 *
 * <p>Doble llave, como el alta de empresas: el permiso {@code moderar_resenas} y ser la
 * plataforma. Una empresa que se concediera el permiso a sí misma seguiría fuera.
 */
public interface ServicioModeracionResenas {

    /**
     * @param resueltos {@code false}: los pendientes, del más antiguo al más reciente;
     *                  {@code true}: los resueltos, el último arriba
     */
    List<ReporteParaModerar> reportes(ContextoUsuario quien, boolean resueltos);

    /** Mantener u ocultar, con una nota. Ocultar es definitivo. */
    ReporteParaModerar resolver(ContextoUsuario quien, Long reporteId, ResolverReporte datos);
}

package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

// El banco es un repositorio, no un cuestionario que se aplica entero (RF-47): cada
// vacante elige de aquí. El ciclo completo: BORRADOR (se edita entero, incluso se
// descarta) → PUBLICADA (se asigna a candidatos; solo admite corrección editorial de
// textos, nunca de claves ni de estructura) → ARCHIVADA (se retira; quien ya la tenía
// la conserva y no se toca, RF-138).
public interface ServicioBancoPreguntas {

    Long crearVersion(ContextoUsuario quien, CrearVersionBanco datos);
    List<VersionBancoResponse> listarVersiones(ContextoUsuario quien);
    void publicarVersion(ContextoUsuario quien, Long id);
    void archivarVersion(ContextoUsuario quien, Long id);

    Long crearPregunta(ContextoUsuario quien, Long versionBancoId, CrearPregunta datos);
    List<PreguntaResponse> listarPreguntas(ContextoUsuario quien, Long versionBancoId);

    Long agregarOpcion(ContextoUsuario quien, Long preguntaId, CrearOpcion datos);
    List<OpcionResponse> listarOpciones(ContextoUsuario quien, Long preguntaId);

    Long agregarRango(ContextoUsuario quien, Long preguntaId, CrearRango datos);
    List<RangoResponse> listarRangos(ContextoUsuario quien, Long preguntaId);

    Long agregarCampoCaso(ContextoUsuario quien, Long preguntaId, CrearCampoCaso datos);
    List<CampoCasoResponse> listarCamposCaso(ContextoUsuario quien, Long preguntaId);

    Long agregarParConsistencia(ContextoUsuario quien, Long versionBancoId, CrearParConsistencia datos);
    List<ParConsistenciaResponse> listarParesConsistencia(ContextoUsuario quien, Long versionBancoId);

    // ---------- La edición de un BORRADOR: reemplazo total, borrado, descarte ----------

    void actualizarPregunta(ContextoUsuario quien, Long preguntaId, CrearPregunta datos);
    void eliminarPregunta(ContextoUsuario quien, Long preguntaId);
    void actualizarOpcion(ContextoUsuario quien, Long opcionId, CrearOpcion datos);
    void eliminarOpcion(ContextoUsuario quien, Long opcionId);
    void actualizarRango(ContextoUsuario quien, Long rangoId, CrearRango datos);
    void eliminarRango(ContextoUsuario quien, Long rangoId);
    void actualizarCampoCaso(ContextoUsuario quien, Long campoId, CrearCampoCaso datos);
    void eliminarCampoCaso(ContextoUsuario quien, Long campoId);
    void actualizarParConsistencia(ContextoUsuario quien, Long parId, CrearParConsistencia datos);
    void eliminarParConsistencia(ContextoUsuario quien, Long parId);
    /** Borra el borrador entero con sus hijas. Un borrador jamás circuló: no es historia. */
    void descartarBorrador(ContextoUsuario quien, Long versionBancoId);

    // ---------- La corrección editorial de una PUBLICADA: textos sí, claves jamás ----------

    void corregirTextoPregunta(ContextoUsuario quien, Long preguntaId, CorregirTextoPregunta datos);
    void corregirTextoOpcion(ContextoUsuario quien, Long opcionId, CorregirTextoOpcion datos);
    void corregirTextoCampoCaso(ContextoUsuario quien, Long campoId, CorregirTextoCampoCaso datos);
    void corregirTextoRango(ContextoUsuario quien, Long rangoId, CorregirTextoRango datos);
    void corregirTextoParConsistencia(ContextoUsuario quien, Long parId, CorregirTextoPar datos);
    void corregirEtiquetaVersion(ContextoUsuario quien, Long versionBancoId, CorregirEtiquetaVersion datos);
}

package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * El perfil visto por el equipo, de solo lectura.
 *
 * <p>La pretensión salarial solo viaja si quien mira tiene el permiso
 * {@code ver_pretension}; sin él, el campo no aparece en el JSON — ni como null. Si
 * apareciera junto a la nota, pesaría en la decisión, que es justo lo que este sistema
 * busca evitar.
 */
public interface ServicioPerfilPanel {

    /** El perfil del candidato de esa postulación. Sin perfil → 200 con todo vacío. */
    PerfilCompleto verDePostulacion(ContextoUsuario quien, Long postulacionId);
}

package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPanel;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioPerfilPanelImpl implements ServicioPerfilPanel {

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final UsuarioRepository usuarios;
    private final PintorDePerfil pintor;
    private final Permisos permisos;

    @Override
    public PerfilCompleto verDePostulacion(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ver_perfil_candidato");
        Long personaId = usuarios.findById(postulacion.getUsuarioId())
                .map(u -> u.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Postulación", "id", postulacionId));

        PerfilCompleto completo = pintor.pintar(personaId);
        // La pretension solo viaja con su propio permiso — y sin el, no viaja ni el
        // nombre del campo (JsonInclude NON_NULL en el DTO).
        return quien.tiene("ver_pretension") ? completo : pintor.sinPretension(completo);
    }

    /**
     * La postulación, si de verdad se puede ver. El filtro por organización no basta: un rol
     * puede tener {@code ver_perfil_candidato} limitado a SUS_VACANTES, y sin esta
     * comprobación leería la trayectoria —y la pretensión salarial— de candidatos de
     * convocatorias ajenas. Es el mismo guardián que usa el resto del panel.
     *
     * <p>Lo que no se puede ver responde 404 y no 403: decir «prohibido» ya confirmaría que
     * ese candidato existe en esa vacante.
     */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        Postulacion p = postulaciones
                .findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Postulación", "id", postulacionId));
        FiltroAlcance alcance = permisos.alcanceDe(permiso);
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES) {
            boolean esSuya = vacantes.findById(p.getVacanteId())
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            if (!esSuya) {
                throw new ResourceNotFoundException("Postulación", "id", postulacionId);
            }
        }
        return p;
    }
}

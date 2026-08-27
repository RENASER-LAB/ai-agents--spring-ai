package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPanel;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioPerfilPanelImpl implements ServicioPerfilPanel {

    private final AlcanceSobreLaVacante alcance;
    private final UsuarioRepository usuarios;
    private final PintorDePerfil pintor;

    /**
     * El perfil de un candidato, si de verdad se puede ver.
     *
     * <p>El filtro por empresa no basta: un rol puede tener {@code ver_perfil_candidato}
     * limitado a sus vacantes, y sin comprobarlo leería la trayectoria —y la pretensión
     * salarial— de candidatos de convocatorias ajenas. Quién alcanza qué lo decide
     * {@link AlcanceSobreLaVacante}, que es el mismo guardián que usa el resto del panel.
     */
    @Override
    public PerfilCompleto verDePostulacion(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion =
                alcance.laPostulacionVisible(quien, postulacionId, "ver_perfil_candidato");
        Long personaId = usuarios.findById(postulacion.getUsuarioId())
                .map(u -> u.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Postulación", "id", postulacionId));

        PerfilCompleto completo = pintor.pintar(personaId);
        // La pretension solo viaja con su propio permiso — y sin el, no viaja ni el
        // nombre del campo (JsonInclude NON_NULL en el DTO).
        return quien.tiene("ver_pretension") ? completo : pintor.sinPretension(completo);
    }
}

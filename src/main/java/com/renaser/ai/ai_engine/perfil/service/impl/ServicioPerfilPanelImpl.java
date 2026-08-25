package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPanel;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioPerfilPanelImpl implements ServicioPerfilPanel {

    private final PostulacionRepository postulaciones;
    private final UsuarioRepository usuarios;
    private final PintorDePerfil pintor;

    @Override
    public PerfilCompleto verDePostulacion(ContextoUsuario quien, Long postulacionId) {
        // El filtro por organizacion es el de siempre: lo de otra organizacion es un 404.
        Long personaId = postulaciones.findByIdAndOrganizacionId(postulacionId,
                        quien.organizacionId())
                .flatMap(p -> usuarios.findById(p.getUsuarioId()))
                .map(u -> u.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Postulación", "id", postulacionId));

        PerfilCompleto completo = pintor.pintar(personaId);
        // La pretension solo viaja con su propio permiso — y sin el, no viaja ni el
        // nombre del campo (JsonInclude NON_NULL en el DTO).
        return quien.tiene("ver_pretension") ? completo : pintor.sinPretension(completo);
    }
}
